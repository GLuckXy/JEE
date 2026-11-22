import { moduleRequest } from '../../api'

const training = '/training'
const session = (id) => training + '/sessions/' + encodeURIComponent(id)
const answer = (id) => training + '/answers/' + encodeURIComponent(id)
const get = (url, params) => moduleRequest('GET', url, undefined, { params })
const post = (url, body, config) => moduleRequest('POST', url, body, config)

export const api = {
  roles: () => get(training + '/roles'),
  questions: (roleId) => get(training + '/questions', { roleId }),
  specialties: (roleId) => get(training + '/specialties', { roleId }),
  history: () => get(training + '/sessions'),
  create: (input) => post(training + '/sessions', input),
  progress: (id) => get(session(id)),
  draft: (id, input) => moduleRequest('PUT', session(id) + '/draft', input),
  action: (id, action, input) => post(session(id) + '/' + action, input),
  submit: (id, input) => post(session(id) + '/answers', input),
  evaluate: (id) => post(answer(id) + '/evaluation', {}, { timeout: 45000 }),
  answer: (id) => get(answer(id)),
  transcribe: (id, input) => post(session(id) + '/transcribe', input, { timeout: 370000 }),
  voiceStatus: () => get('/voice/status'),
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return post('/voice/audio', form)
  },
  speech: (text) => post('/voice/speech', { text }, { responseType: 'blob', timeout: 75000 }),
}

export const audioPath = (id) => '/voice/audio/' + encodeURIComponent(id)
export const sessionPath = (mode, id) => (mode === 'SPECIAL' ? '/practice/' : '/training/') + id
export const message = (error) => error?.message || '请求失败，请检查网络或稍后重试'
export const duration = (ms) => {
  const seconds = Math.floor(Math.max(0, ms) / 1000)
  return [Math.floor(seconds / 60), seconds % 60].map((part) => String(part).padStart(2, '0')).join(':')
}
export const stateLabel = (state) => ({ ACTIVE: '进行中', PAUSED: '已暂停', ENDED: '已结束' })[state]
