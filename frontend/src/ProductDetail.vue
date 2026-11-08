<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElButton, ElDialog, ElSkeleton } from 'element-plus'
import { formatParameter, formatPrice, imageFallback, loadProduct } from './catalog'
import { formatPerformanceScore } from './performance'
import { useCatalogRequest } from './useCatalogRequest'
import ProductPerformanceTags from './ProductPerformanceTags.vue'
import { useCart } from './cart'

const props = defineProps<{ id: string; signedIn?: boolean }>()
const emit = defineEmits<{ close: []; signIn: [] }>()
const { data: detail, status, error, retryable, refresh } = useCatalogRequest(
  signal => loadProduct(props.id, signal), '商品详情')
const { add, error: cartError, notice: cartNotice } = useCart()
const adding = ref(false)
watch(() => props.id, refresh, { immediate: true })

async function addToCart() {
  if (!detail.value || adding.value) return
  adding.value = true
  await add(detail.value.product.id, 1)
  adding.value = false
}
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
          <div class="detail-cart">
            <ElButton v-if="!signedIn" type="primary" @click="emit('signIn')">登录后加入购物车</ElButton>
            <ElButton v-else type="primary" :loading="adding" :disabled="adding || detail.product.stock === 0" @click="addToCart">
              {{ detail.product.stock === 0 ? '暂时缺货，无法加入购物车' : '加入购物车' }}
            </ElButton>
            <p v-if="cartError" role="alert" class="auth-error">{{ cartError }}</p>
            <p v-else-if="cartNotice" role="status" class="auth-success">{{ cartNotice }}<a href="#cart">查看购物车</a></p>
            <p v-else class="detail-demo-note">每次加入 1 件；同一商品会在购物车合并，可再调整 1～99 的数量。</p>
          </div>
        </div>
      </div>
      <dl class="detail-identity">
        <dt>商品 ID</dt><dd>{{ detail.product.id }}</dd><dt>销售 SKU</dt><dd>{{ detail.product.sku }}</dd>
        <dt>品牌</dt><dd>{{ detail.product.brand }}</dd><dt>型号</dt><dd>{{ detail.product.model }}</dd>
        <dt>图片地址</dt><dd><a :href="detail.product.imageUrl" target="_blank" rel="noopener noreferrer">{{ detail.product.imageUrl }}</a></dd>
        <dt>型号来源</dt><dd><a :href="detail.product.sourceUrl" target="_blank" rel="noopener noreferrer">厂商资料 ↗</a></dd>
      </dl>
      <section class="detail-performance-tags"><h3>性能标签</h3><ProductPerformanceTags :tags="detail.product.performanceTags"/></section>
      <section v-if="detail.performance" class="performance-public" aria-label="性能参考">
        <h3>性能参考</h3>
        <p><strong class="performance-score">{{ detail.performance.score === null ? '暂无有效参考分' : `${detail.performance.performanceScore ? '综合评分 ' : ''}${formatPerformanceScore(detail.performance.score)} / 100` }}</strong> · {{ detail.performance.note }}</p>
        <p v-if="detail.performance.performanceScore">性能子分 {{ formatPerformanceScore(detail.performance.performanceScore) }} / 100（仅比较性能参数）</p>
        <small>映射版本 {{ detail.performance.mappingVersion }}</small>
      </section>
      <h3>分类参数</h3>
      <table class="parameter-table"><thead><tr><th scope="col">参数</th><th scope="col">数值 / 支持集合</th><th scope="col">资料</th></tr></thead><tbody>
        <tr v-for="parameter in detail.parameters" :key="parameter.code"><th scope="row">{{ parameter.label }}</th><td><span :class="{ 'unverified-value': parameter.value === null }">{{ formatParameter(parameter) }}</span><small v-if="parameter.note">{{ parameter.note }}</small></td><td><a :href="parameter.sourceUrl" target="_blank" rel="noopener noreferrer" :aria-label="`${parameter.label}的厂商来源`">来源 ↗</a></td></tr>
      </tbody></table>
    </div>
    <template #footer><ElButton @click="emit('close')">关闭详情</ElButton><ElButton v-if="status === 'ready'" type="primary" @click="refresh">刷新价格与库存</ElButton></template>
  </ElDialog>
</template>
