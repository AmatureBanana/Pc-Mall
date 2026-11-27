import { computed, ref, type Ref } from 'vue'
import type { Category, Product, ProductPlatform } from './catalog'

const memoryCapacities: Record<string, number[]> = { '8G': [8], '16G': [16], '24G': [24], '32G': [32] }
const ssdCapacities: Record<string, number[]> = {
  '500G': [480, 500, 512],
  '1T': [960, 1000, 1024],
  '2T': [1920, 2000, 2048],
  '4T': [4000, 4096],
  '8T': [8000, 8192],
}

export function useProductFilters(products: Readonly<Ref<Product[] | undefined>>, category: Readonly<Ref<Category>>) {
  const brand = ref('')
  const platform = ref<ProductPlatform | ''>('')
  const capacity = ref('')
  const ratedPower = ref<number | null>(null)
  const ratedPowers = computed(() => {
    if (category.value !== 'PSU') return []
    const values = new Set((products.value ?? []).map(product => product.ratedPowerWatts).filter(power => power !== undefined))
    if (ratedPower.value !== null) values.add(ratedPower.value)
    return [...values].sort((a, b) => a - b)
  })
  const capacityGroups = computed(() => category.value === 'MEMORY' ? memoryCapacities : category.value === 'SSD' ? ssdCapacities : {})
  const capacities = computed(() => Object.keys(capacityGroups.value))
  const selectedCapacities = computed(() => capacityGroups.value[capacity.value])
  const platforms = computed<ProductPlatform[]>(() => {
    if (category.value === 'MOTHERBOARD') return ['Intel', 'AMD']
    if (category.value === 'GPU') return ['NVIDIA', 'AMD', 'Intel']
    return []
  })
  const platformProducts = computed(() => (products.value ?? []).filter(product => !platform.value || product.platform === platform.value))
  const availableBrands = computed(() => [...new Set(platformProducts.value.map(product => product.brand))])
  const brands = computed(() => brand.value && !availableBrands.value.includes(brand.value)
    ? [brand.value, ...availableBrands.value] : availableBrands.value)
  const filteredProducts = computed(() => platformProducts.value.filter(product =>
    (!brand.value || product.brand === brand.value)
    && (ratedPower.value === null || product.ratedPowerWatts === ratedPower.value)
    && (!selectedCapacities.value || product.capacityGb !== undefined && selectedCapacities.value.includes(product.capacityGb))))

  function selectPlatform(value: ProductPlatform | '') {
    platform.value = value
    if (!availableBrands.value.includes(brand.value)) brand.value = ''
  }

  return { brand, platform, brands, platforms, capacity, capacities, ratedPower, ratedPowers, filteredProducts, selectPlatform }
}
