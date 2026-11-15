import {test} from 'node:test'
import assert from 'node:assert/strict'
import {reactive} from 'vue'
import {mergeParsed,changes,clonePayload} from '../src/mergeParsed.ts'
const blank=()=>({skills:[],education:[],internships:[],projects:[]})
const experience=(organization,period='',title='',description='')=>({organization,period,title,description})
const project=(name,responsibility='')=>({name,responsibility,goal:'',techStack:[],difficulty:'',solution:'',result:'',improvement:''})
test('reactive drafts preserve manual content and fill only empty matching fields',()=>{
  const current=reactive({...blank(),skills:['SQL'],education:[experience('测试大学','2023—2027','手填专业')]})
  const candidate=reactive({...blank(),skills:['Java'],education:[experience('测试大学','2023—2027','解析专业','解析说明')]})
  const result=mergeParsed(current,candidate).payload
  assert.equal(result.education[0].title,'手填专业')
  assert.equal(result.education[0].description,'解析说明')
  assert.deepEqual(result.skills,['SQL'])
  assert.equal(current.education[0].description,'')
  assert.deepEqual(clonePayload(candidate),JSON.parse(JSON.stringify(candidate)))
})
test('matches projects by name even when reordered, and appends new entries',()=>{
  const current={...blank(),projects:[project('B'),project('A','本人填写')]}
  const candidate={...blank(),projects:[project('A','解析A'),project('B','解析B'),project('C','解析C')]}
  const result=mergeParsed(current,candidate).payload
  assert.deepEqual(result.projects.map(p=>p.responsibility),['解析B','本人填写','解析C'])
})
test('same organization with different dates stays separate',()=>{
  const result=mergeParsed({...blank(),internships:[experience('公司','2024—2025')]},{...blank(),internships:[experience('公司','2025—2026')]}).payload
  assert.equal(result.internships.length,2)
})
test('ambiguous and unnamed entries are skipped rather than overwritten by position',()=>{
  const result=mergeParsed({...blank(),projects:[project('A'),project('A'),project('')]},{...blank(),projects:[project('A','待核对'),project('B','新增')]})
  assert.equal(result.skipped.length,2)
  assert.ok(result.payload.projects.every(p=>!p.responsibility))
})
test('diff includes replaced and removed content before approval',()=>{
  const current={...blank(),projects:[project('A','旧职责')]}
  const next={...blank(),projects:[project('B')]}
  const diff=changes(current,next)
  assert.ok(diff.some(d=>d.before==='旧职责'&&d.after===''))
  assert.ok(diff.some(d=>d.before==='A'&&d.after==='B'))
})
