export interface User { id:string; account:string; nickname:string; role:'STUDENT'|'ADMIN'; status:'ACTIVE'|'DISABLED'; email:string|null; phone:string|null; school:string|null; major:string|null; createdAt:string; updatedAt:string }
export interface Page<T> {total:number; page:number; size:number; records:T[]}
export interface Experience {organization:string; title:string; period:string; description:string}
export interface ProjectFields {name:string; goal:string; responsibility:string; techStack:string[]; difficulty:string; solution:string; result:string; improvement:string}
export interface Payload {education:Experience[]; skills:string[]; projects:ProjectFields[]; internships:Experience[]}
export interface Summary {id:string; fileId:string; originalName:string; parseStatus:'UPLOADED'|'PARSING'|'SUCCESS'|'FAILED'; confirmStatus:'DRAFT'|'CONFIRMED'; version:number; revision:number; failureReason:string|null; createdAt:string; confirmedAt:string|null}
export interface Evidence {field:string;source:string;confidence:'MATCHED'|'REVIEW'}
export interface Detail {summary:Summary; rawParseResult:{text:string;extracted:Payload;parserVersion:string;evidence?:Evidence[];warnings?:string[]}|null; draft:Payload; confirmedPayload:Payload|null; allowedActions:string[]}
export interface ResumeVersion {id:string; version:number; payload:Payload; confirmedAt:string}
export interface Project extends ProjectFields {id:string; resumeId:string;version:number;sortNo:number}
export interface Selection {resumeId:string;projectId:string;version:number;snapshot:Project}
export const emptyProject=():ProjectFields=>({name:'',goal:'',responsibility:'',techStack:[],difficulty:'',solution:'',result:'',improvement:''})
export const emptyExperience=():Experience=>({organization:'',title:'',period:'',description:''})
export function dateTime(value:string|null) { if(!value)return '—'; const d=new Date(/(?:Z|[+-]\d\d:\d\d)$/.test(value)?value:value+'Z'); return Number.isNaN(d.getTime())?'—':d.toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}) }
