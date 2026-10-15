<script setup lang="ts" name="Mall">
import { computed, onBeforeMount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import apis from '@/services/apis'
import type { MallShop, MallShopType, MallVoucher, MallVoucherOrder } from '@/services/types'

/** 分 */
const fen2yuan = (v?: number) => ((v ?? 0) / 100).toFixed(2)
/** 评分 48 -> 4.8 */
const score2star = (v?: number) => ((v ?? 0) / 10).toFixed(1)
const dist2str = (d?: number) =>
  d == null ? '' : d < 1000 ? `${Math.round(d)}m` : `${(d / 1000).toFixed(1)}km`

const shopTypes = ref<MallShopType[]>([])
const activeTypeId = ref<number | null>(null)
const shops = ref<MallShop[]>([])
const loadingShop = ref(false)
/** 用户位置（浏览器 geolocation 或手动定位按钮） */
const userPos = ref<{ x: number; y: number } | null>(null)
const locating = ref(false)

const loadTypes = () => {
  apis.getShopTypeList().send().then((list) => {
    shopTypes.value = list ?? []
  })
}

const loadShops = () => {
  if (activeTypeId.value == null) return
  loadingShop.value = true
  const params: any = { typeId: activeTypeId.value, current: 1 }
  if (userPos.value) {
    params.x = userPos.value.x
    params.y = userPos.value.y
  }
  apis
    .getShopOfPage(params)
    .send()
    .then((list) => {
      shops.value = list ?? []
    })
    .finally(() => (loadingShop.value = false))
}

const selectType = (id: number | null) => {
  activeTypeId.value = id
  loadShops()
}

/** 用浏览器定位，拿不到就退化为普通分页 */
const locate = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      userPos.value = { x: pos.coords.longitude, y: pos.coords.latitude }
      locating.value = false
      loadShops()
    },
    () => {
      locating.value = false
      ElMessage.warning('定位失败，已按普通分页展示')
    },
    { timeout: 8000 },
  )
}

const firstImg = (images?: string) => (images ? images.split(',')[0] : '')

// ---------- 商户详情抽屉 ----------
const drawerShow = ref(false)
const currentShop = ref<MallShop | null>(null)
const vouchers = ref<MallVoucher[]>([])
const loadingVoucher = ref(false)
/** 已下单的券 id 集合，防止重复点 */
const orderingIds = ref<Set<number>>(new Set())

const openShop = (shop: MallShop) => {
  currentShop.value = shop
  drawerShow.value = true
  loadingVoucher.value = true
  apis
    .getShopVoucherList(shop.id)
    .send()
    .then((list) => (vouchers.value = list ?? []))
    .finally(() => (loadingVoucher.value = false))
}

// ---------- 我的订单 ----------
const orderShow = ref(false)
const orders = ref<MallVoucherOrder[]>([])
const loadingOrder = ref(false)
const orderVoucherMap = ref<Record<number, MallVoucher>>({})

const openOrders = () => {
  orderShow.value = true
  loadingOrder.value = true
  apis
    .getMyVoucherOrders()
    .send()
    .then(async (list) => {
      orders.value = list ?? []
      // 拉取订单对应的券标题
      const ids = [...new Set(orders.value.map((o) => o.voucherId))]
      const seckills = (await apis.getSeckillVoucherList().send().catch(() => [])) ?? []
      const map: Record<number, MallVoucher> = {}
      seckills.forEach((v) => (map[v.id] = v))
      // 普通券从所属商户兜底查询
      for (const id of ids) {
        if (!map[id] && seckills.length === 0) break
      }
      orderVoucherMap.value = map
    })
    .finally(() => (loadingOrder.value = false))
}

const voucherTitle = computed(() => (vid: number) => orderVoucherMap.value[vid]?.title ?? `券 #${vid}`)

onBeforeMount(() => {
  loadTypes()
  // 默认选第一个类型
  apis.getShopTypeList().send().then((list) => {
    if (list?.length) {
      activeTypeId.value = list[0].id
      loadShops()
    }
  })
})
</script>

<template>
  <div class="mall-page">
    <header class="mall-header">
      <span class="mall-title">商城</span>
      <div class="mall-actions">
        <el-button size="small" :loading="locating" @click="locate">
          {{ userPos ? '已定位·按距离排序' : '定位按距离排序' }}
        </el-button>
        <el-button size="small" @click="openOrders">我的订单</el-button>
      </div>
    </header>

    <nav class="mall-types">
      <span
        class="mall-type-item"
        :class="{ active: activeTypeId === t.id }"
        v-for="t in shopTypes"
        :key="t.id"
        @click="selectType(t.id)"
      >
        {{ t.name }}
      </span>
    </nav>

    <main class="mall-body" v-loading="loadingShop">
      <div class="shop-card" v-for="s in shops" :key="s.id" @click="openShop(s)">
        <el-image class="shop-img" :src="firstImg(s.images)" fit="cover">
          <template #error>
            <div class="shop-img-err">暂无图片</div>
          </template>
        </el-image>
        <div class="shop-info">
          <div class="shop-name">{{ s.name }}</div>
          <div class="shop-meta">
            <span class="shop-score">{{ score2star(s.score) }} 分</span>
            <span>{{ fen2yuan(s.avgPrice) }} 元/人</span>
            <span v-if="s.distance != null" class="shop-dist">{{ dist2str(s.distance) }}</span>
          </div>
          <div class="shop-area">{{ s.area }} · {{ s.address }}</div>
        </div>
      </div>
      <div v-if="!loadingShop && shops.length === 0" class="mall-empty">该类型下暂无商户~</div>
    </main>

    <!-- 商户详情 + 优惠券 -->
    <el-drawer v-model="drawerShow" :title="currentShop?.name" size="420px">
      <div class="voucher-box" v-loading="loadingVoucher">
        <div class="shop-detail">
          <div class="shop-detail-row">
            <span>评分</span><b>{{ score2star(currentShop?.score) }}</b>
          </div>
          <div class="shop-detail-row">
            <span>人均</span><b>{{ fen2yuan(currentShop?.avgPrice) }} 元</b>
          </div>
          <div class="shop-detail-row">
            <span>已售</span><b>{{ currentShop?.sold ?? 0 }}</b>
          </div>
          <div class="shop-detail-row">
            <span>营业</span><b>{{ currentShop?.openHours || '-' }}</b>
          </div>
          <div class="shop-detail-row">
            <span>地址</span><b>{{ currentShop?.address }}</b>
          </div>
        </div>
        <div class="voucher-title">优惠券</div>
        <div class="voucher-item" v-for="v in vouchers" :key="v.id">
          <div class="voucher-price">
            <span class="pay">￥{{ fen2yuan(v.payValue) }}</span>
            <span class="actual">价值 {{ fen2yuan(v.actualValue) }}</span>
          </div>
          <div class="voucher-info">
            <div class="v-name">{{ v.title }}</div>
            <div class="v-sub">{{ v.subTitle }}</div>
          </div>
        </div>
        <div v-if="!loadingVoucher && vouchers.length === 0" class="mall-empty">该商户暂无优惠券~</div>
      </div>
    </el-drawer>

    <!-- 我的订单 -->
    <el-drawer v-model="orderShow" title="我的优惠券订单" size="420px">
      <div v-loading="loadingOrder">
        <div class="order-item" v-for="o in orders" :key="o.id">
          <div class="order-name">{{ voucherTitle(o.voucherId) }}</div>
          <div class="order-meta">
            <span>订单号 {{ o.id }}</span>
            <span :class="o.status === 1 ? 'ok' : ''">
              {{ o.status === 1 ? '未使用' : `状态 ${o.status}` }}
            </span>
          </div>
          <div class="order-time">{{ o.createTime }}</div>
        </div>
        <div v-if="!loadingOrder && orders.length === 0" class="mall-empty">还没有订单，去抢一张吧~</div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.mall-page {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  overflow: hidden;
}

.mall-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 4px;

  .mall-title {
    font-size: 18px;
    font-weight: 600;
  }
}

.mall-types {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 16px;

  .mall-type-item {
    padding: 4px 12px;
    border-radius: 14px;
    cursor: pointer;
    font-size: 13px;
    background: var(--hover-bg-1, #f5f5f5);
    transition: all 0.15s;

    &:hover {
      background: var(--hover-bg-2, #e8e8e8);
    }

    &.active {
      background: var(--el-color-primary);
      color: #fff;
    }
  }
}

.mall-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px 16px;
}

.shop-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: var(--hover-bg-1, #f5f5f5);
  }

  .shop-img {
    width: 92px;
    height: 92px;
    border-radius: 8px;
    flex-shrink: 0;
  }

  .shop-img-err {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    background: var(--hover-bg-1, #f0f0f0);
  }

  .shop-info {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    min-width: 0;

    .shop-name {
      font-size: 15px;
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .shop-meta {
      display: flex;
      gap: 10px;
      font-size: 12px;
      color: var(--el-text-color-secondary);

      .shop-score {
        color: var(--el-color-warning);
        font-weight: 600;
      }

      .shop-dist {
        color: var(--el-color-primary);
      }
    }

    .shop-area {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}

.mall-empty {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: 40px 0;
  font-size: 13px;
}

.shop-detail {
  margin-bottom: 16px;

  .shop-detail-row {
    display: flex;
    justify-content: space-between;
    padding: 6px 0;
    font-size: 13px;

    span {
      color: var(--el-text-color-secondary);
    }

    b {
      font-weight: 500;
      text-align: right;
    }
  }
}

.voucher-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
}

.voucher-item {
  display: flex;
  gap: 12px;
  padding: 10px;
  border: 1px dashed var(--el-color-danger-light-5, #fab6b6);
  border-radius: 8px;
  margin-bottom: 10px;

  .voucher-price {
    display: flex;
    flex-direction: column;
    justify-content: center;
    min-width: 80px;

    .pay {
      color: var(--el-color-danger);
      font-size: 18px;
      font-weight: 700;
    }

    .actual {
      font-size: 11px;
      color: var(--el-text-color-secondary);
    }
  }

  .voucher-info {
    min-width: 0;

    .v-name {
      font-size: 13px;
      font-weight: 500;
    }

    .v-sub {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-top: 4px;
      white-space: pre-wrap;
    }
  }
}

.order-item {
  padding: 10px;
  border-radius: 8px;
  background: var(--hover-bg-1, #f7f7f7);
  margin-bottom: 10px;

  .order-name {
    font-size: 14px;
    font-weight: 500;
  }

  .order-meta {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 6px;

    .ok {
      color: var(--el-color-success);
    }
  }

  .order-time {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 2px;
  }
}
</style>
