<template>
  <direct-chat
    :subtitle="thread.subtitle"
    :messages="thread.messages || []"
    :mine-role="2"
    :loading="loading"
    :sending="sending"
    @send="onSend"
  />
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import DirectChat from '@/components/direct-chat/direct-chat.vue'
import {
  fetchStudentThread,
  sendStudentMessage,
  markStudentRead
} from '@/api/student-portal'

const thread = ref({})
const loading = ref(true)
const sending = ref(false)

onShow(load)

async function load() {
  loading.value = true
  try {
    thread.value = (await fetchStudentThread()) || {}
    /* 导航栏写班主任姓名（后端取不到名字时 title 是「班主任」，正好可以直接用） */
    if (thread.value.title) {
      uni.setNavigationBarTitle({ title: thread.value.title })
    }
    /* 打开就算读过。放在拉取之后、不 await ——
       已读失败不该影响看消息，标不上最多下次再标 */
    markStudentRead().catch(() => {})
  } catch (e) {
    /* 1002 已由 request.js 处理 */
  } finally {
    loading.value = false
  }
}

async function onSend(text) {
  if (sending.value) return
  sending.value = true
  try {
    const msg = await sendStudentMessage(text)
    if (msg) {
      thread.value = {
        ...thread.value,
        messages: [...(thread.value.messages || []), msg]
      }
    }
  } catch (e) {
    /* 失败时消息没发出去。这里不把文字塞回输入框 ——
       组件已经清空了，塞回去会和用户接下来打的字打架。
         toast 由 request.js 弹过了 */
  } finally {
    sending.value = false
  }
}
</script>
