import { randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const base = process.env.VERIFY_BASE_URL || 'http://127.0.0.1:8080';
const results = [];
const stamp = new Date().toISOString();
function check(name, condition) {
  results.push({ name, passed: Boolean(condition) });
  console.log(`${condition ? 'PASS' : 'FAIL'} ${name}`);
  if (!condition) throw new Error(`Verification failed: ${name}`);
}
async function call(method, path, token, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body && !(body instanceof FormData)) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(body); }
  const response = await fetch(base + path, { method, headers, body, signal: AbortSignal.timeout(60000) });
  const bytes = Buffer.from(await response.arrayBuffer());
  const json = response.headers.get('content-type')?.includes('application/json') ? JSON.parse(bytes.toString('utf8')) : null;
  return { status: response.status, data: json?.data, code: json?.code, bytes };
}
function pdf(text = true) {
  const stream = text ? 'BT /F1 12 Tf 50 700 Td (Skills) Tj 0 -20 Td (Java, SQL) Tj 0 -20 Td (Projects) Tj 0 -20 Td (Verification Platform) Tj ET' : '';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`
  ];
  let doc = '%PDF-1.4\n'; const offsets = [0];
  objects.forEach((obj, i) => { offsets.push(Buffer.byteLength(doc)); doc += `${i + 1} 0 obj\n${obj}\nendobj\n`; });
  const xref = Buffer.byteLength(doc);
  doc += `xref\n0 6\n0000000000 65535 f \n${offsets.slice(1).map(x => String(x).padStart(10, '0') + ' 00000 n ').join('\n')}\ntrailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(doc);
}
const metadata = { timestamp: stamp, baseUrl: base, accounts: [], results };
try {
  const actors = [];
  for (let i = 0; i < 2; i++) {
    const account = `flow${randomBytes(5).toString('hex')}`;
    const password = randomBytes(24).toString('base64url') + 'Aa1';
    const register = await call('POST', '/api/auth/register', null, { account, password, nickname: `流程验证${i + 1}` });
    check(`学生${i + 1}注册`, register.status === 200 && register.data.role === 'STUDENT');
    metadata.accounts.push({ account, id: register.data.id });
    const login = await call('POST', '/api/auth/login', null, { account, password });
    check(`学生${i + 1}登录`, login.status === 200 && Boolean(login.data.accessToken));
    actors.push({ id: register.data.id, token: login.data.accessToken });
  }
  const [a, b] = actors;
  check('本人资料查询', (await call('GET', '/api/users/me', a.token)).data.id === a.id);
  check('个人资料修改', (await call('PUT', '/api/users/me', a.token, { nickname: '流程验证学生', school: '合成测试学校' })).status === 200);
  async function upload(buffer, name) {
    const form = new FormData(); form.append('file', new Blob([buffer], { type: 'application/pdf' }), name);
    const r = await call('POST', '/api/resumes', a.token, form);
    check(`${name}上传`, r.status === 200); return r.data;
  }
  const bytes = pdf();
  let resume = await upload(bytes, 'flow-text.pdf');
  const id = resume.summary.id, fileId = resume.summary.fileId;
  metadata.resumeId = id; metadata.fileId = fileId;
  check('PDF真实文本提取', resume.summary.parseStatus === 'SUCCESS' && resume.rawParseResult.text.includes('Verification Platform'));
  check('简历列表包含本人上传', (await call('GET', '/api/resumes', a.token)).data.records.some(x => x.id === id));
  check('未确认项目不可训练', (await call('GET', `/api/resumes/${id}/projects`, a.token)).code === 'RESUME_NOT_CONFIRMED');
  const download = await call('GET', `/api/files/${fileId}`, a.token);
  check('本人下载内容与上传一致', download.status === 200 && download.bytes.equals(bytes));
  const payload = { education: [], skills: ['Java', 'MyBatis-Plus'], internships: [], projects: [{ name: '流程验证项目', goal: '合成测试', responsibility: '本人实现测试模块', techStack: ['Java', 'MySQL'], difficulty: '', solution: '', result: '', improvement: '' }] };
  const draft = await call('PUT', `/api/resumes/${id}/parsed-data`, a.token, { expectedRevision: resume.summary.revision, payload });
  check('校对草稿保存', draft.status === 200 && draft.data.draft.projects[0].responsibility === payload.projects[0].responsibility);
  const confirmed = await call('POST', `/api/resumes/${id}/confirm`, a.token, { expectedRevision: draft.data.summary.revision });
  check('确认生成版本1', confirmed.status === 200 && confirmed.data.summary.version === 1 && confirmed.data.summary.confirmStatus === 'CONFIRMED');
  const projects = await call('GET', `/api/resumes/${id}/projects`, a.token);
  check('获取已确认项目', projects.status === 200 && projects.data.length === 1);
  const pid = projects.data[0].id; metadata.projectId = pid;
  check('项目详情', (await call('GET', `/api/resume-projects/${pid}`, a.token)).data.responsibility === payload.projects[0].responsibility);
  const selected = await call('POST', `/api/resumes/${id}/select-project`, a.token, { projectId: pid, version: 1 });
  check('项目选择返回正确训练快照', selected.status === 200 && selected.data.snapshot.responsibility === payload.projects[0].responsibility && selected.data.version === 1);
  for (const path of [`/api/resumes/${id}`, `/api/resumes/${id}/projects`, `/api/resumes/${id}/versions`, `/api/resume-projects/${pid}`, `/api/files/${fileId}`])
    check(`第二账号不可读取 ${path}`, (await call('GET', path, b.token)).status === 404);
  check('第二账号不可修改草稿', (await call('PUT', `/api/resumes/${id}/parsed-data`, b.token, { expectedRevision: 2, payload })).status === 404);
  check('第二账号不可确认他人简历', (await call('POST', `/api/resumes/${id}/confirm`, b.token, { expectedRevision: 2 })).status === 404);
  check('第二账号不可选择他人项目', (await call('POST', `/api/resumes/${id}/select-project`, b.token, { projectId: pid, version: 1 })).status === 404);
  check('第二账号列表不泄露简历', (await call('GET', '/api/resumes', b.token)).data.total === 0);
  check('学生不可访问后台', (await call('GET', '/api/admin/users', a.token)).status === 403);
  check('不允许资料修改角色', (await call('PUT', '/api/users/me', a.token, { nickname: '测试', role: 'ADMIN' })).status === 400);
  const nextPayload = structuredClone(payload); nextPayload.projects[0].responsibility = '更新后的本人职责';
  const edited = await call('PUT', `/api/resumes/${id}/parsed-data`, a.token, { expectedRevision: confirmed.data.summary.revision, payload: nextPayload });
  check('编辑后回到待确认', edited.status === 200 && edited.data.summary.confirmStatus === 'DRAFT');
  check('编辑后暂不可用于训练', (await call('GET', `/api/resumes/${id}/projects`, a.token)).status === 409);
  const v2 = await call('POST', `/api/resumes/${id}/confirm`, a.token, { expectedRevision: edited.data.summary.revision });
  check('再次确认生成版本2', v2.status === 200 && v2.data.summary.version === 2);
  const history = await call('GET', `/api/resumes/${id}/versions`, a.token);
  check('版本1历史内容保持不变', history.status === 200 && history.data.find(x => x.version === 1).payload.projects[0].responsibility === payload.projects[0].responsibility);
  check('旧项目不再作为当前版本可选项目', (await call('GET', `/api/resume-projects/${pid}`, a.token)).status === 404);
  const current = await call('GET', `/api/resumes/${id}/projects`, a.token); metadata.currentProjectId = current.data[0].id;
  check('旧选择版本被拒', (await call('POST', `/api/resumes/${id}/select-project`, a.token, { projectId: metadata.currentProjectId, version: 1 })).code === 'VERSION_CONFLICT');
  const scan = await upload(pdf(false), 'flow-no-text.pdf'); metadata.manualResumeId = scan.summary.id;
  check('无文本PDF标记解析失败', scan.summary.parseStatus === 'FAILED');
  const manualDraft = await call('PUT', `/api/resumes/${scan.summary.id}/parsed-data`, a.token, { expectedRevision: 0, payload });
  check('失败简历可保存人工草稿', manualDraft.status === 200);
  const manual = await call('POST', `/api/resumes/${scan.summary.id}/confirm`, a.token, { expectedRevision: manualDraft.data.summary.revision });
  check('解析失败仍可人工确认', manual.status === 200 && manual.data.summary.parseStatus === 'FAILED' && manual.data.summary.confirmStatus === 'CONFIRMED');
  for (let i = 0; i < actors.length; i++) {
    const actor = actors[i];
    check(`学生${i + 1}退出`, (await call('POST', '/api/auth/logout', actor.token)).status === 200);
    check(`学生${i + 1}退出后令牌失效`, (await call('GET', '/api/users/me', actor.token)).status === 401);
  }
  metadata.success = true;
} catch (error) {
  metadata.success = false;
  console.error(error.message.startsWith('Verification failed:') ? error.message : `Flow execution failed (${error.name}); credentials and responses withheld`);
  process.exitCode = 1;
} finally {
  const output = new URL('../target/verification/', import.meta.url);
  await mkdir(output, { recursive: true });
  const path = fileURLToPath(new URL(`mysql-flow-${stamp.replaceAll(/[:.]/g, '-')}.json`, output));
  await writeFile(path, JSON.stringify(metadata, null, 2));
  console.log(`Report: ${path}; ${results.filter(x => x.passed).length}/${results.length} passed; test records retained`);
}
