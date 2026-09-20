/**
 * 后端接口回归测试
 *
 * 用法（后端要先启动）：
 *   node scripts/api-test.mjs
 *
 * 用 node 执行而不是 curl，是为了避开 Windows 控制台的中文编码问题
 * （curl 在 GBK 控制台下发中文 JSON 会报 Invalid UTF-8 start byte，见文档「坑 5」）
 *
 * 脚本会自己创建测试数据并在结束时删掉，不会污染你的表。
 */

const BASE = 'http://localhost:8080/api/todos'
const JSON_HEADER = { 'Content-Type': 'application/json; charset=utf-8' }

let passCount = 0
let failCount = 0

function check(name, ok, detail = '') {
  if (ok) {
    passCount++
    console.log(`  \u2713 ${name}`)
  } else {
    failCount++
    console.log(`  \u2717 ${name}${detail ? '  -> ' + detail : ''}`)
  }
}

async function call(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: body ? JSON_HEADER : undefined,
    body: body ? JSON.stringify(body) : undefined
  })
  const text = await res.text()
  let data
  try {
    data = JSON.parse(text)
  } catch {
    data = text
  }
  return { httpStatus: res.status, data }
}

async function main() {
  console.log('后端接口回归测试\n' + '='.repeat(50))

  // ---------- 1. 统计 ----------
  console.log('\n[1] GET /stats  统计数据')
  const before = await call('GET', '/stats')
  console.log('  返回: ' + JSON.stringify(before.data.data))
  check('HTTP 200', before.httpStatus === 200)
  check('code 为 200', before.data.code === 200)
  check('total = finished + pending',
    before.data.data.total === before.data.data.finished + before.data.data.pending)

  // ---------- 2. 新增（含中文与截止时间） ----------
  console.log('\n[2] POST /  新增一条含中文的待办')
  const created = await call('POST', '', {
    title: '接口验证 · 中文与 emoji 测试 🎯',
    description: '这段描述用来验证 UTF-8 中文能否正确存取',
    deadline: '2026-09-25 10:00:00'
  })
  console.log('  返回: ' + JSON.stringify(created.data))
  check('HTTP 200', created.httpStatus === 200)
  check('code 为 200', created.data.code === 200)

  const todo = created.data.data
  const newId = todo && todo.id
  check('自增主键已回填', typeof newId === 'number' && newId > 0, 'id=' + newId)
  check('中文标题存取正确', todo && todo.title === '接口验证 · 中文与 emoji 测试 🎯')
  check('done 默认为 false', todo && todo.done === false)
  check('数据库自动填充了 createTime', !!todo?.createTime, todo?.createTime)
  check('deadline 格式正确', todo && todo.deadline === '2026-09-25 10:00:00', todo?.deadline)

  // ---------- 3. 按 id 查详情 ----------
  console.log(`\n[3] GET /${newId}  按 id 查详情`)
  const detail = await call('GET', `/${newId}`)
  check('HTTP 200', detail.httpStatus === 200)
  check('查到的 id 一致', detail.data.data?.id === newId)

  // ---------- 4. 修改 ----------
  // 先等 1.1 秒：MySQL 的 DATETIME 默认精度到秒，
  // 如果新增和更新落在同一秒，updateTime 看起来会「没变」
  await new Promise(resolve => setTimeout(resolve, 1100))
  console.log(`\n[4] PUT /${newId}  修改标题与描述`)
  const updated = await call('PUT', `/${newId}`, {
    title: '标题已经被改过了',
    description: '修改后的描述'
  })
  console.log('  返回: ' + JSON.stringify(updated.data.data))
  check('HTTP 200', updated.httpStatus === 200)
  check('标题已更新', updated.data.data?.title === '标题已经被改过了')
  check('未传的 done 字段保持原值', updated.data.data?.done === false)
  check('updateTime 已刷新', updated.data.data?.updateTime !== todo.updateTime,
    `${todo.updateTime} -> ${updated.data.data?.updateTime}`)

  // ---------- 5. 切换完成状态 ----------
  console.log(`\n[5] PATCH /${newId}/done  切换完成状态`)
  const toggled1 = await call('PATCH', `/${newId}/done`)
  check('第一次切换 -> done = true', toggled1.data.data?.done === true)
  const toggled2 = await call('PATCH', `/${newId}/done`)
  check('再切一次 -> done = false', toggled2.data.data?.done === false)

  // ---------- 6. 关键词搜索 ----------
  console.log('\n[6] GET /?keyword=Vue  标题模糊搜索')
  const searched = await call('GET', '?keyword=Vue')
  const list = searched.data.data || []
  check('HTTP 200', searched.httpStatus === 200)
  check('至少命中一条', list.length > 0, '命中 ' + list.length + ' 条')
  check('命中结果标题都含 Vue', list.every(t => t.title.includes('Vue')))

  // ---------- 7. done 过滤 ----------
  console.log('\n[7] GET /?done=false  排除已完成')
  const pendingOnly = await call('GET', '?done=false')
  const plist = pendingOnly.data.data || []
  check('HTTP 200', pendingOnly.httpStatus === 200)
  check('返回结果全部未完成', plist.every(t => t.done === false), plist.length + ' 条')

  // ---------- 8. 参数校验 ----------
  console.log('\n[8] POST /  故意不传标题，验证全局异常处理')
  const invalid = await call('POST', '', { description: '这条没有标题' })
  console.log('  返回: ' + JSON.stringify(invalid.data))
  check('code 为 400', invalid.data.code === 400)
  check('返回了友好提示', invalid.data.message === '标题不能为空', invalid.data.message)
  check('响应结构与成功接口一致', 'code' in invalid.data && 'message' in invalid.data)

  // ---------- 9. 查询不存在的 id ----------
  console.log('\n[9] GET /999999  查询不存在的记录')
  const notFound = await call('GET', '/999999')
  check('code 为 404', notFound.data.code === 404)
  check('提示语正确', notFound.data.message === '待办不存在', notFound.data.message)

  // ---------- 10. 删除 ----------
  console.log(`\n[10] DELETE /${newId}  删除测试数据`)
  const deleted = await call('DELETE', `/${newId}`)
  check('HTTP 200', deleted.httpStatus === 200)
  check('code 为 200', deleted.data.code === 200)
  const afterDelete = await call('GET', `/${newId}`)
  check('删除后确实查不到了', afterDelete.data.code === 404)

  // ---------- 11. 数据已还原 ----------
  console.log('\n[11] 收尾检查')
  const finalStats = await call('GET', '/stats')
  check('统计数据回到测试前的值',
    finalStats.data.data.total === before.data.data.total,
    `测试前 ${before.data.data.total} 条 -> 现在 ${finalStats.data.data.total} 条`)

  console.log('\n' + '='.repeat(50))
  console.log(`通过 ${passCount} 项，失败 ${failCount} 项`)
  if (failCount > 0) process.exit(1)
}

main().catch(err => {
  console.error('测试执行失败:', err.message)
  process.exit(1)
})
