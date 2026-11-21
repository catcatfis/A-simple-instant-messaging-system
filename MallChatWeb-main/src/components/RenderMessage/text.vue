<script setup lang="ts">
import { computed } from 'vue'
import type { TextBody } from '@/services/types'

const props = defineProps<{ body: TextBody }>()

// 获取所有匹配的字符串
const urlMap = props.body.urlContentMap || {}
const keys = Object.keys(urlMap)

/**
 * 转义正则元字符：链接来自其他用户的消息内容，
 * 未转义时形如 a(b) 的地址会让 new RegExp 抛 SyntaxError，导致整条消息渲染失败。
 */
const escapeRegExp = (str: string) => str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

// 使用匹配字符串创建动态正则表达式，并将文本拆分为片段数组
const fragments = computed(() => {
  let content = props.body.content || ''
  content = content.replace(/&nbsp;/g, '\u00A0')
  const escapedKeys = keys.filter((key) => key.trim() !== '').map(escapeRegExp)
  const urlPart = escapedKeys.length > 0 ? `${escapedKeys.join('|')}|` : ''
  const regex = new RegExp(`(@\\S+\\s|${urlPart}\\S+\\s)`, 'g')
  return content.split(regex)
})

// 打开链接
const openUrl = (url: string) => {
  if (!url) return
  // 仅放行 http/https，避免 javascript: 等危险协议；无协议时按 https 补全
  let target: URL
  try {
    target = new URL(/^https?:\/\//i.test(url) ? url : `https://${url}`)
  } catch {
    return
  }
  if (target.protocol !== 'http:' && target.protocol !== 'https:') return
  window.open(target.href, '_blank', 'noopener,noreferrer')
}

function onImageLoadError(e: Event) {
  const target = e.target as HTMLImageElement
  if (!target) return
  target.style.opacity = '0'
}
</script>

<template>
  <div class="text">
    <template v-for="(item, index) in fragments">
      <span
        v-if="item.startsWith('@') && item.trim() !== '' && item.trim() !== '@'"
        :key="item"
        class="text-mention"
      >
        {{ item }}
      </span>
      <template v-else>{{ item }}</template>
      <div
        v-if="keys.includes(item)"
        :key="item + index"
        rel="noopener noreferrer nofollow"
        target="_blank"
        class="text-card"
        @click="openUrl(item.trim())"
      >
        <div v-if="urlMap[item].image" class="text-card-image-wrapper">
          <img class="text-card-image" :src="urlMap[item].image" @error="onImageLoadError" />
        </div>
        <div class="text-card-link-content">
          <!-- <span class="text-card-link ellipsis-1"> {{ item.trim() }}</span> -->
          <span class="text-card-title ellipsis-1"> {{ urlMap[item].title }}</span>
          <span class="text-card-desc ellipsis-2">{{ urlMap[item].description }}</span>
        </div>
      </div>
    </template>
  </div>
</template>
