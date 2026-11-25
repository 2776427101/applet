<script>
/* 构建时间戳，由 vite.config.js 的 define 注入。
   用 typeof 判断是因为：万一某个构建入口没走 define，直接引用会 ReferenceError
   把整个小程序启动打挂 —— 一个调试用的标记不值得冒这个险 */
const BUILD_STAMP = typeof __BUILD_STAMP__ !== 'undefined' ? __BUILD_STAMP__ : 'unknown'

export default {
  onLaunch: function () {
    /* 这一行是排查"设备上是不是旧包"的唯一可靠依据。
       报错和它一起看：时间对不上 = 包没更新，别再怀疑代码 */
    console.log(`[班级助手] 构建于 ${BUILD_STAMP}`)
  },
  onShow: function () {
    console.log('App Show')
  },
  onHide: function () {
    console.log('App Hide')
  },
}
</script>

<style>
/*每个页面公共css */
</style>
