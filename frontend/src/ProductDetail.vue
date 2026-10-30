<script setup lang="ts">
import { watch } from 'vue'
import { ElButton, ElDialog, ElSkeleton } from 'element-plus'
import { formatParameter, formatPrice, imageFallback, loadProduct } from './catalog'
import { useCatalogRequest } from './useCatalogRequest'

const props = defineProps<{ id: string }>()
const emit = defineEmits<{ close: [] }>()
const { data: detail, status, error, retryable, refresh } = useCatalogRequest(
  signal => loadProduct(props.id, signal), '商品详情')
watch(() => props.id, refresh, { immediate: true })
</script>

<template>
  <ElDialog :model-value="true" title="商品详情" class="product-detail-dialog" width="min(920px, calc(100vw - 32px))" top="5vh" @update:model-value="emit('close')">
    <div v-if="status === 'loading'" aria-label="正在加载商品详情" aria-busy="true"><ElSkeleton animated :rows="8"/></div>
    <div v-else-if="status === 'error'" class="detail-error" role="alert"><h2>无法查看商品详情</h2><p>{{ error }}</p><ElButton v-if="retryable" type="primary" @click="refresh">重新加载详情</ElButton></div>
    <div v-else-if="detail" class="product-detail">
      <div class="detail-overview">
        <div class="detail-image"><img :src="detail.product.imageUrl" :alt="`${detail.product.categoryLabel}品类示意图`" @error="imageFallback"/><span>品类示意图</span></div>
        <div><p class="detail-category">{{ detail.product.categoryLabel }} · {{ detail.product.brand }}</p><h2>{{ detail.product.name }}</h2><p class="detail-description">{{ detail.description }}</p>
          <div class="detail-price"><span class="product-price">¥ {{ formatPrice(detail.product.price) }}</span><span class="stock" :class="{ unavailable: detail.product.stock === 0 }">{{ detail.product.stock === 0 ? '暂时缺货' : `库存 ${detail.product.stock} 件` }}</span></div>
          <p class="detail-demo-note">{{ detail.product.priceNote }}</p>
        </div>
      </div>
      <dl class="detail-identity">
        <dt>商品 ID</dt><dd>{{ detail.product.id }}</dd><dt>销售 SKU</dt><dd>{{ detail.product.sku }}</dd>
        <dt>品牌</dt><dd>{{ detail.product.brand }}</dd><dt>型号</dt><dd>{{ detail.product.model }}</dd>
        <dt>图片地址</dt><dd><a :href="detail.product.imageUrl" target="_blank" rel="noopener noreferrer">{{ detail.product.imageUrl }}</a></dd>
        <dt>型号来源</dt><dd><a :href="detail.product.sourceUrl" target="_blank" rel="noopener noreferrer">厂商资料 ↗</a></dd>
      </dl>
      <h3>分类参数</h3>
      <table class="parameter-table"><thead><tr><th scope="col">参数</th><th scope="col">数值 / 支持集合</th><th scope="col">资料</th></tr></thead><tbody>
        <tr v-for="parameter in detail.parameters" :key="parameter.code"><th scope="row">{{ parameter.label }}</th><td><span :class="{ 'unverified-value': parameter.value === null }">{{ formatParameter(parameter) }}</span><small v-if="parameter.note">{{ parameter.note }}</small></td><td><a :href="parameter.sourceUrl" target="_blank" rel="noopener noreferrer" :aria-label="`${parameter.label}的厂商来源`">来源 ↗</a></td></tr>
      </tbody></table>
    </div>
    <template #footer><ElButton @click="emit('close')">关闭详情</ElButton><ElButton v-if="status === 'ready'" type="primary" @click="refresh">刷新价格与库存</ElButton></template>
  </ElDialog>
</template>
