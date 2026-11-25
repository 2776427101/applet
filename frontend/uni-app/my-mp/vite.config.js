import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

/* 构建时间戳。
 *
 * 为什么需要：真机/模拟器上出现「xx is not a function」这类报错时，
 * 第一步要确认的永远是"设备上跑的到底是哪一版包"。没有这个标记，
 * 就只能靠猜 —— 我们已经在"清缓存了但报错没变"上浪费过两轮。
 *
 * 启动时会打印到控制台：如果这个时间和刚构建的时间对不上，
 * 说明设备/开发者工具还在用旧包，不用再往下查代码。
 */
const BUILD_STAMP = new Date().toLocaleString('zh-CN', { hour12: false })

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    uni(),
  ],
  define: {
    __BUILD_STAMP__: JSON.stringify(BUILD_STAMP),
  },
})
