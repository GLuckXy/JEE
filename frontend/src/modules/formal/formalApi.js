import { moduleRequest } from '../../api'

export function createFormalApi() {
  const base = '/formal-interviews'
  const call = (path) => moduleRequest('GET', base + path)
  const post = (path, data, timeout = 30000) => moduleRequest('POST', base + path, data, { timeout })

  return {
    options: () => call('/options'),
    create: (roleId, projectId) => post('', { roleId, projectId }),
    get: (id) => call(`/${id}`),
    start: (id, turn) => post(`/${id}/turns/${turn}/start`),
    stop: (id, turn, finish) => post(`/${id}/turns/${turn}/stop?finish=${finish}`),
    upload(id, turn, blob) {
      const form = new FormData()
      form.append('audio', blob, 'answer')
      return post(`/${id}/turns/${turn}/audio`, form, 370000)
    },
    retry: (id, turn) => post(`/${id}/turns/${turn}/retry`, undefined, 370000),
    skipFollowUp: (id, turn) => post(`/${id}/turns/${turn}/skip-follow-up`),
    end: (id, reason) => post(`/${id}/end`, { reason }),
  }
}
