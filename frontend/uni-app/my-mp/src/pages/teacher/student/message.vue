<template>
  <direct-chat
    :subtitle="thread.subtitle"
    :messages="thread.messages || []"
    :mine-role="1"
    :loading="loading"
    :sending="sending"
    @send="onSend"
  />
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import DirectChat from '@/components/direct-chat/direct-chat.vue'
import { fetchThread, sendToStudent, markThreadRead } from '@/api/message'

const thread = ref({})
const loading = ref(true)
const sending = ref(false)
let studentId = 0

onLoad((options) => {
  /* 学生 id 只从 query 里来，页面不自己猜。
     传错了后端会按班级归属拦掉（3002 / 1004），不会串到别的学生 */
  studentId = Number(options && options.id) || 0
  if (!studentId) {
    uni.showToast({ title: '缺少学生', icon: 'none' })
    return
  }
  load()
})

async function load() {
  loading.value = true
  try {
    thread.value = (await fetchThread(studentId)) || {}
    /* 导航栏写学生姓名。pages.json 里的「私信」只是个占位标题 ——
       从通讯录连着点进来好几个学生，标题一直写「私信」就分不清在跟谁说话 */
    if (thread.value.title) {
      uni.setNavigationBarTitle({ title: thread.value.title })
    }
    markThreadRead(studentId).catch(() => {})
  } catch (e) {
    /* 已由 request.js 提示 */
  } finally {
    loading.value = false
  }
}

async function onSend(text) {
  if (sending.value) return
  sending.value = true
  try {
    const msg = await sendToStudent(studentId, text)
    if (msg) {
      thread.value = {
        ...thread.value,
        messages: [...(thread.value.messages || []), msg]
      }
    }
  } catch (e) {
    /* 已由 request.js 提示 */
  } finally {
    sending.value = false
  }
}
</script>
