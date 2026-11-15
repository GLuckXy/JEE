import type {Payload,Experience,ProjectFields} from './types'
export interface Change {field:string;before:string;after:string}
// Payloads contain JSON fields only; this also accepts Vue reactive proxies.
export function clonePayload(value:Payload):Payload{return JSON.parse(JSON.stringify(value))}
function key(value:string){return (value||'').normalize('NFKC').replace(/\s/g,'').toLowerCase()}
function empty(value:unknown){return value==null||typeof value==='string'&&!value.trim()||Array.isArray(value)&&value.length===0}
const labels={education:'教育经历',internships:'实习经历',projects:'项目经历'} as const
/** Match by organization + period, or project name. Never merge unrelated entries by array index. */
export function mergeParsed(current:Payload,candidate:Payload){
  const result=clonePayload(current),parsed=clonePayload(candidate),skipped:string[]=[]
  if(!result.skills.length)result.skills=parsed.skills
  for(const section of ['education','internships','projects'] as const){
    const dest=result[section] as (Experience|ProjectFields)[]
    for(const item of parsed[section]){
      const identity='name' in item?key(item.name):key(item.organization)
      if(!identity){skipped.push(`${labels[section]}中有未确定名称的条目，未自动合并`);continue}
      const matches=dest.filter(existing=>{
        if('name'in existing&&'name'in item)return key(existing.name)===identity
        if('organization'in existing&&'organization'in item)return key(existing.organization)===identity&&(!existing.period?.trim()||!item.period?.trim()||key(existing.period)===key(item.period))
        return false
      })
      if(matches.length>1){skipped.push(`${labels[section]}“${'name'in item?item.name:item.organization}”存在多个匹配，未自动合并`);continue}
      if(!matches.length){
        // A manually entered blank name may refer to the same source entry; avoid guessing or creating duplicates.
        if(dest.some(existing=>'name'in existing?!existing.name?.trim():!existing.organization?.trim())){skipped.push(`${labels[section]}中已有未命名条目，请手动核对后合并`);continue}
        if(dest.length<30)dest.push(structuredClone(item));else skipped.push(`${labels[section]}已达30项`)
        continue
      }
      const target=matches[0] as unknown as Record<string,unknown>
      for(const [field,value]of Object.entries(item))if(empty(target[field])&&!empty(value))target[field]=structuredClone(value)
    }
  }
  return {payload:result,skipped}
}
const fieldLabels:Record<string,string>={organization:'学校/单位',title:'专业/岗位',period:'起止时间',description:'经历说明',name:'项目名称',goal:'项目目标',responsibility:'本人职责',techStack:'技术栈',difficulty:'困难',solution:'解决过程',result:'成果',improvement:'改进'}
export function changes(current:Payload,next:Payload):Change[]{
  const output:Change[]=[]
  const display=(value:unknown)=>Array.isArray(value)?value.join('、'):String(value??'')
  if(JSON.stringify(current.skills)!==JSON.stringify(next.skills))output.push({field:'技能',before:display(current.skills),after:display(next.skills)})
  for(const section of ['education','internships','projects']as const){
    const length=Math.max(current[section].length,next[section].length)
    for(let i=0;i<length;i++){
      const before=current[section][i] as unknown as Record<string,unknown>|undefined,after=next[section][i] as unknown as Record<string,unknown>|undefined
      for(const field of new Set([...Object.keys(before||{}),...Object.keys(after||{})]))if(JSON.stringify(before?.[field]??'')!==JSON.stringify(after?.[field]??''))output.push({field:`${labels[section]} ${i+1} · ${fieldLabels[field]||field}`,before:display(before?.[field]),after:display(after?.[field])})
    }
  }
  return output
}
