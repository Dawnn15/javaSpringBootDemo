/**
 * 后端接口封装
 *
 * 所有请求都走 /api 前缀，由 vite.config.js 里的 proxy 转发到 http://localhost:8080
 * 这样浏览器看到的是「同源请求」，不会有跨域问题。
 */

const BASE = '/api/todos'

/**
 * 统一请求方法
 * 后端所有接口都返回 { code, message, data } 结构，
 * 这里做一次统一拆包：成功就 resolve data，失败就 throw。
 */
async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })

  if (!res.ok) {
    throw new Error(`请求失败 HTTP ${res.status}`)
  }

  const body = await res.json()

  if (body.code !== 200) {
    throw new Error(body.message || '操作失败')
  }

  return body.data
}

/** 把 JS 对象拼成 URL 查询串，自动跳过空值 */
function buildQuery(params) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, value)
    }
  })
  const qs = query.toString()
  return qs ? `?${qs}` : ''
}

export const todoApi = {
  /** 查询列表，支持 done 过滤和 keyword 模糊搜索 */
  list(params = {}) {
    return request(`${BASE}${buildQuery(params)}`)
  },

  /** 统计数据：总数 / 已完成 / 未完成 */
  stats() {
    return request(`${BASE}/stats`)
  },

  /** 新增 */
  create(data) {
    return request(BASE, {
      method: 'POST',
      body: JSON.stringify(data)
    })
  },

  /** 修改 */
  update(id, data) {
    return request(`${BASE}/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data)
    })
  },

  /** 切换完成状态 */
  toggle(id) {
    return request(`${BASE}/${id}/done`, { method: 'PATCH' })
  },

  /** 删除 */
  remove(id) {
    return request(`${BASE}/${id}`, { method: 'DELETE' })
  }
}
