<template>
  <div class="exchange-page">
    <section class="summary-grid" aria-label="设备兑换概览">
      <div class="summary-item">
        <div class="summary-icon green"><el-icon><Goods /></el-icon></div>
        <div>
          <span>兑换商品</span>
          <strong>{{ productTotal }}</strong>
        </div>
      </div>
      <div class="summary-item">
        <div class="summary-icon graphite"><el-icon><Tickets /></el-icon></div>
        <div>
          <span>兑换订单</span>
          <strong>{{ statistics.totalOrders || 0 }}</strong>
        </div>
      </div>
      <div class="summary-item">
        <div class="summary-icon amber"><el-icon><Clock /></el-icon></div>
        <div>
          <span>待发货</span>
          <strong>{{ statistics.pendingOrders || 0 }}</strong>
        </div>
      </div>
      <div class="summary-item">
        <div class="summary-icon teal"><el-icon><CircleCheck /></el-icon></div>
        <div>
          <span>已到货</span>
          <strong>{{ statistics.completedOrders || 0 }}</strong>
        </div>
      </div>
    </section>

    <section class="exchange-workspace">
      <div class="workspace-head">
        <el-tabs v-model="activeView" class="view-tabs" @tab-change="handleViewChange">
          <el-tab-pane label="兑换商品" name="products" />
          <el-tab-pane label="兑换订单" name="orders" />
        </el-tabs>
        <div class="workspace-actions">
          <el-button :icon="Refresh" :loading="currentLoading" @click="refreshCurrent">刷新</el-button>
          <el-button v-if="activeView === 'products'" type="primary" :icon="Plus" @click="openProductDialog()">
            新增设备商品
          </el-button>
        </div>
      </div>

      <div v-show="activeView === 'products'" class="view-content">
        <div class="filter-bar">
          <el-input
            v-model="productFilters.keyword"
            clearable
            placeholder="搜索商品名称"
            :prefix-icon="Search"
            @clear="searchProducts"
            @keyup.enter="searchProducts"
          />
          <el-select v-model="productFilters.status" clearable placeholder="全部状态" @change="searchProducts">
            <el-option label="已上架" :value="1" />
            <el-option label="已下架" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="searchProducts">查询</el-button>
          <el-button @click="resetProductFilters">重置</el-button>
        </div>

        <el-table :data="products" border stripe v-loading="productLoading" empty-text="暂无兑换商品">
          <el-table-column label="设备商品" min-width="260">
            <template #default="{ row }">
              <div class="product-cell">
                <el-image
                  class="product-thumb"
                  :src="row.imageUrl || ''"
                  fit="cover"
                  :preview-src-list="row.imageUrl ? [row.imageUrl] : []"
                  preview-teleported
                >
                  <template #error><div class="image-fallback"><el-icon><Picture /></el-icon></div></template>
                </el-image>
                <div class="product-copy">
                  <strong>{{ row.name }}</strong>
                  <span>ID：{{ row.id }}</span>
                  <small>{{ row.description || '暂无描述' }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="普通价格" width="120" align="right">
            <template #default="{ row }"><strong class="money">¥{{ formatMoney(row.basePrice) }}</strong></template>
          </el-table-column>
          <el-table-column label="等级价格" min-width="310">
            <template #default="{ row }">
              <div class="level-price-list">
                <span v-for="level in visiblePriceLevels" :key="level.field">
                  <b>{{ level.label }}</b>
                  ¥{{ formatMoney(row[level.field] ?? row.basePrice) }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="库存" width="90" align="center">
            <template #default="{ row }">
              <span :class="['stock-value', { empty: Number(row.stock || 0) <= 0 }]">{{ row.stock || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                {{ row.status === 1 ? '已上架' : '已下架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="270" fixed="right" align="center">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain :icon="Edit" @click="openProductDialog(row)">编辑</el-button>
              <el-button
                size="small"
                :type="row.status === 1 ? 'warning' : 'success'"
                plain
                @click="toggleProduct(row)"
              >
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
              <el-button size="small" type="danger" plain :icon="Delete" @click="deleteProduct(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-row">
          <el-pagination
            v-model:current-page="productPage"
            v-model:page-size="productPageSize"
            :total="productTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchProducts"
            @current-change="fetchProducts"
          />
        </div>
      </div>

      <div v-show="activeView === 'orders'" class="view-content">
        <div class="filter-bar">
          <el-input
            v-model="orderFilters.keyword"
            clearable
            placeholder="订单号、商品、收货人或手机号"
            :prefix-icon="Search"
            @clear="searchOrders"
            @keyup.enter="searchOrders"
          />
          <el-select v-model="orderFilters.status" clearable placeholder="全部状态" @change="searchOrders">
            <el-option v-for="option in orderStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="searchOrders">查询</el-button>
          <el-button @click="resetOrderFilters">重置</el-button>
        </div>

        <el-table :data="orders" border stripe v-loading="orderLoading" empty-text="暂无兑换订单">
          <el-table-column label="订单" min-width="190">
            <template #default="{ row }">
              <div class="order-number">
                <strong>{{ row.orderNo }}</strong>
                <span>{{ formatTime(row.createTime) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="兑换用户" min-width="180">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar :size="34" :src="row.avatarUrl || ''">{{ (row.nickname || '用户').charAt(0) }}</el-avatar>
                <div>
                  <strong>{{ row.nickname || '未知用户' }}</strong>
                  <span>ID：{{ row.userId }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="兑换设备" min-width="220">
            <template #default="{ row }">
              <div class="order-product">
                <el-image class="order-product-image" :src="row.productImage || ''" fit="cover">
                  <template #error><div class="image-fallback"><el-icon><Picture /></el-icon></div></template>
                </el-image>
                <div>
                  <strong>{{ row.productName || '-' }}</strong>
                  <span>数量 × {{ row.quantity || 1 }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="兑换金额" width="140" align="right">
            <template #default="{ row }">
              <div class="order-amount">
                <strong>¥{{ formatMoney(row.totalPrice) }}</strong>
                <span>{{ formatHashrate(row.hashrateCost) }} 算力值</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="收货信息" min-width="190">
            <template #default="{ row }">
              <div class="receiver-cell">
                <strong>{{ row.receiverName || '-' }}</strong>
                <span>{{ row.receiverPhone || '-' }}</span>
                <small>{{ row.receiverAddress || '-' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="orderStatusType(row.status)" effect="plain">{{ orderStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="物流" min-width="170">
            <template #default="{ row }">
              <div v-if="row.expressNo" class="logistics-cell">
                <strong>{{ row.expressCompany || '快递' }}</strong>
                <span>{{ row.expressNo }}</span>
              </div>
              <span v-else class="muted">尚未发货</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="290" fixed="right" align="center">
            <template #default="{ row }">
              <el-button size="small" :icon="View" @click="viewOrder(row)">详情</el-button>
              <el-button v-if="row.status === 0" size="small" type="primary" :icon="Van" @click="openShipDialog(row)">发货</el-button>
              <el-button v-if="row.status === 0" size="small" type="danger" plain @click="returnOrder(row)">退回</el-button>
              <el-button v-if="row.status === 1" size="small" type="primary" plain @click="advanceOrder(row, 2)">运输中</el-button>
              <el-button v-if="row.status === 2" size="small" type="success" plain @click="advanceOrder(row, 3)">已到货</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-row">
          <el-pagination
            v-model:current-page="orderPage"
            v-model:page-size="orderPageSize"
            :total="orderTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchOrders"
            @current-change="fetchOrders"
          />
        </div>
      </div>
    </section>

    <el-dialog
      v-model="productDialogVisible"
      :title="productForm.id ? '编辑兑换设备' : '新增兑换设备'"
      width="780px"
      destroy-on-close
    >
      <el-form ref="productFormRef" :model="productForm" :rules="productRules" label-position="top">
        <div class="product-form-grid">
          <el-form-item label="设备名称" prop="name" class="span-2">
            <el-input v-model="productForm.name" maxlength="100" show-word-limit placeholder="请输入设备名称" />
          </el-form-item>

          <el-form-item label="主图" prop="imageUrl" class="span-2">
            <div class="image-editor">
              <el-upload
                class="product-uploader"
                name="file"
                :show-file-list="false"
                accept=".jpg,.jpeg,.png,.gif,.webp,.bmp"
                :http-request="uploadProductImage"
                :before-upload="beforeProductImageUpload"
                :disabled="productImageUploading"
              >
                <img v-if="productForm.imageUrl" :src="productForm.imageUrl" alt="" />
                <div v-else-if="productImageUploading" class="upload-empty">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>上传中</span>
                </div>
                <div v-else class="upload-empty">
                  <el-icon><Picture /></el-icon>
                  <span>上传主图</span>
                </div>
              </el-upload>
              <el-input v-model="productForm.imageUrl" clearable placeholder="图片地址" />
            </div>
          </el-form-item>

          <el-form-item label="普通用户价格" prop="basePrice">
            <el-input-number v-model="productForm.basePrice" :min="0.01" :precision="2" :step="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="库存" prop="stock">
            <el-input-number v-model="productForm.stock" :min="0" :precision="0" :step="1" controls-position="right" />
          </el-form-item>

          <el-form-item v-for="level in visiblePriceLevels" :key="level.field" :label="`${level.label}价格`">
            <el-input-number
              v-model="productForm[level.field]"
              :min="0.01"
              :precision="2"
              :step="1"
              controls-position="right"
              placeholder="默认普通价格"
            />
          </el-form-item>

          <el-form-item label="排序">
            <el-input-number v-model="productForm.sortOrder" :min="0" :max="999999" :precision="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="上架状态">
            <el-switch v-model="productForm.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
          </el-form-item>

          <el-form-item label="设备描述" prop="description" class="span-2">
            <el-input v-model="productForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="请输入设备描述" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="productDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="productSaving" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shipDialogVisible" title="订单发货" width="520px" destroy-on-close>
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-position="top">
        <el-form-item label="订单号">
          <el-input :model-value="shippingOrder?.orderNo || ''" disabled />
        </el-form-item>
        <el-form-item label="快递公司" prop="expressCompany">
          <el-select v-model="shipForm.expressCompany" filterable allow-create default-first-option placeholder="请选择或输入快递公司">
            <el-option v-for="company in expressCompanies" :key="company" :label="company" :value="company" />
          </el-select>
        </el-form-item>
        <el-form-item label="快递单号" prop="expressNo">
          <el-input v-model="shipForm.expressNo" maxlength="100" placeholder="请输入快递单号" />
        </el-form-item>
        <el-form-item label="管理员备注">
          <el-input v-model="shipForm.adminRemark" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="orderSubmitting" @click="confirmShip">确认发货</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orderDetailVisible" title="兑换订单详情" width="760px">
      <div v-if="currentOrder" v-loading="orderDetailLoading" class="order-detail">
        <div class="detail-product">
          <el-image class="detail-product-image" :src="currentOrder.productImage || ''" fit="cover">
            <template #error><div class="image-fallback"><el-icon><Picture /></el-icon></div></template>
          </el-image>
          <div>
            <strong>{{ currentOrder.productName || '-' }}</strong>
            <span>数量 × {{ currentOrder.quantity || 1 }}</span>
          </div>
          <el-tag :type="orderStatusType(currentOrder.status)" effect="plain">{{ orderStatusText(currentOrder.status) }}</el-tag>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatTime(currentOrder.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ currentOrder.nickname || '未知用户' }}（ID：{{ currentOrder.userId }}）</el-descriptions-item>
          <el-descriptions-item label="下单等级">{{ levelName(currentOrder.userLevel) }}</el-descriptions-item>
          <el-descriptions-item label="单价">¥{{ formatMoney(currentOrder.unitPrice) }}</el-descriptions-item>
          <el-descriptions-item label="总价">¥{{ formatMoney(currentOrder.totalPrice) }}</el-descriptions-item>
          <el-descriptions-item label="消耗算力值">{{ formatHashrate(currentOrder.hashrateCost) }}</el-descriptions-item>
          <el-descriptions-item label="邀请分润">¥{{ formatMoney(currentOrder.inviterProfit) }}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{ currentOrder.receiverName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ currentOrder.receiverPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ currentOrder.receiverAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物流公司">{{ currentOrder.expressCompany || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物流单号">{{ currentOrder.expressNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户备注" :span="2">{{ currentOrder.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="管理员备注" :span="2">{{ currentOrder.adminRemark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="orderLogistics.length > 0" class="logistics-history">
          <h3>物流记录</h3>
          <el-timeline>
            <el-timeline-item
              v-for="item in orderLogistics"
              :key="item.id"
              :timestamp="formatTime(item.createTime)"
              :type="orderStatusType(item.status)"
              placement="top"
            >
              <strong>{{ item.description || orderStatusText(item.status) }}</strong>
              <span>{{ item.operator || '系统' }}</span>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  CircleCheck,
  Clock,
  Delete,
  Edit,
  Goods,
  Loading,
  Picture,
  Plus,
  Refresh,
  Search,
  Tickets,
  Van,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { uploadImageFile } from '../utils/upload'

const activeView = ref('products')
const productLoading = ref(false)
const productImageUploading = ref(false)
const orderLoading = ref(false)
const productSaving = ref(false)
const orderSubmitting = ref(false)
const ordersLoaded = ref(false)

const products = ref([])
const productPage = ref(1)
const productPageSize = ref(10)
const productTotal = ref(0)
const productFilters = reactive({ keyword: '', status: '' })

const orders = ref([])
const orderPage = ref(1)
const orderPageSize = ref(10)
const orderTotal = ref(0)
const orderFilters = reactive({ keyword: '', status: '' })

const statistics = reactive({
  totalOrders: 0,
  pendingOrders: 0,
  shippedOrders: 0,
  completedOrders: 0,
  cancelledOrders: 0,
})

const levelNames = ref(['等级1', '等级2', '等级3', '等级4', '等级5'])
const configuredLevelCount = ref(5)
const visiblePriceLevels = computed(() => {
  const count = Math.min(5, Math.max(1, configuredLevelCount.value))
  return Array.from({ length: count }, (_, index) => ({
    field: `priceLevel${index + 1}`,
    label: index === 4 && configuredLevelCount.value > 5
      ? `${levelNames.value[index] || `等级${index + 1}`}及以上`
      : (levelNames.value[index] || `等级${index + 1}`),
  }))
})

const currentLoading = computed(() => activeView.value === 'products' ? productLoading.value : orderLoading.value)

const orderStatusOptions = [
  { label: '待发货', value: 0 },
  { label: '已发货', value: 1 },
  { label: '运输中', value: 2 },
  { label: '已到货', value: 3 },
  { label: '已取消', value: 4 },
]

const expressCompanies = ['顺丰速运', '京东物流', '中通快递', '圆通速递', '申通快递', '韵达快递', '邮政EMS', '德邦快递']

const productDialogVisible = ref(false)
const productFormRef = ref(null)
const productForm = reactive(createEmptyProduct())
const productRules = {
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  basePrice: [{ required: true, message: '请输入普通用户价格', trigger: 'change' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'change' }],
}

const shipDialogVisible = ref(false)
const shipFormRef = ref(null)
const shippingOrder = ref(null)
const shipForm = reactive({ expressCompany: '', expressNo: '', adminRemark: '' })
const shipRules = {
  expressCompany: [{ required: true, message: '请选择或输入快递公司', trigger: 'change' }],
  expressNo: [{ required: true, message: '请输入快递单号', trigger: 'blur' }],
}

const orderDetailVisible = ref(false)
const orderDetailLoading = ref(false)
const currentOrder = ref(null)
const orderLogistics = ref([])

function createEmptyProduct() {
  return {
    id: null,
    name: '',
    description: '',
    imageUrl: '',
    images: null,
    basePrice: null,
    priceLevel1: null,
    priceLevel2: null,
    priceLevel3: null,
    priceLevel4: null,
    priceLevel5: null,
    stock: 0,
    status: 1,
    sortOrder: 0,
  }
}

const resetProductForm = (product = null) => {
  Object.assign(productForm, createEmptyProduct(), product || {})
}

const reportRequestError = (action, error) => {
  console.error(`${action}失败:`, error)
}

const loadLevelSettings = async () => {
  try {
    const res = await request.get('/api/settings/all', { silent: true })
    if (res.data.code !== 200) return
    const levels = Array.isArray(res.data.data?.inviteLevels) ? res.data.data.inviteLevels : []
    if (levels.length > 0) {
      configuredLevelCount.value = levels.length
      levelNames.value = Array.from({ length: 5 }, (_, index) => levels[index]?.name || `等级${index + 1}`)
    }
  } catch (error) {
    reportRequestError('加载等级名称', error)
  }
}

const fetchStatistics = async () => {
  try {
    const res = await request.get('/api/admin/exchange/statistics', { silent: true })
    if (res.data.code === 200) Object.assign(statistics, res.data.data || {})
  } catch (error) {
    reportRequestError('加载兑换统计', error)
  }
}

const fetchProducts = async () => {
  productLoading.value = true
  try {
    const res = await request.get('/api/admin/exchange/products', {
      params: {
        page: productPage.value,
        size: productPageSize.value,
        keyword: productFilters.keyword.trim() || undefined,
        status: productFilters.status === '' ? undefined : productFilters.status,
      },
    })
    if (res.data.code === 200) {
      products.value = res.data.data?.records || []
      productTotal.value = Number(res.data.data?.total || 0)
    } else {
      ElMessage.error(res.data.msg || '获取兑换商品失败')
    }
  } catch (error) {
    reportRequestError('获取兑换商品', error)
  } finally {
    productLoading.value = false
  }
}

const fetchOrders = async () => {
  orderLoading.value = true
  try {
    const res = await request.get('/api/admin/exchange/orders', {
      params: {
        page: orderPage.value,
        size: orderPageSize.value,
        keyword: orderFilters.keyword.trim() || undefined,
        status: orderFilters.status === '' ? undefined : orderFilters.status,
      },
    })
    if (res.data.code === 200) {
      orders.value = res.data.data?.records || []
      orderTotal.value = Number(res.data.data?.total || 0)
      ordersLoaded.value = true
    } else {
      ElMessage.error(res.data.msg || '获取兑换订单失败')
    }
  } catch (error) {
    reportRequestError('获取兑换订单', error)
  } finally {
    orderLoading.value = false
  }
}

const handleViewChange = (name) => {
  if (name === 'orders' && !ordersLoaded.value) fetchOrders()
}

const refreshCurrent = async () => {
  await Promise.all([
    activeView.value === 'products' ? fetchProducts() : fetchOrders(),
    fetchStatistics(),
  ])
}

const searchProducts = () => {
  productPage.value = 1
  fetchProducts()
}

const resetProductFilters = () => {
  productFilters.keyword = ''
  productFilters.status = ''
  searchProducts()
}

const searchOrders = () => {
  orderPage.value = 1
  fetchOrders()
}

const resetOrderFilters = () => {
  orderFilters.keyword = ''
  orderFilters.status = ''
  searchOrders()
}

const openProductDialog = (product = null) => {
  resetProductForm(product ? { ...product } : null)
  productDialogVisible.value = true
}

const saveProduct = async () => {
  const valid = await productFormRef.value?.validate().catch(() => false)
  if (!valid) return

  productSaving.value = true
  try {
    const payload = {
      ...productForm,
      name: productForm.name.trim(),
      description: productForm.description?.trim() || '',
      imageUrl: productForm.imageUrl?.trim() || '',
    }
    const res = await request.post('/api/admin/exchange/product', payload)
    if (res.data.code === 200) {
      ElMessage.success(res.data.msg || '保存成功')
      productDialogVisible.value = false
      await fetchProducts()
    } else {
      ElMessage.error(res.data.msg || '保存失败')
    }
  } catch (error) {
    reportRequestError('保存兑换商品', error)
  } finally {
    productSaving.value = false
  }
}

const toggleProduct = async (product) => {
  try {
    const res = await request.post(`/api/admin/exchange/product/${product.id}/toggle`)
    if (res.data.code === 200) {
      ElMessage.success(res.data.msg || '操作成功')
      await fetchProducts()
    } else {
      ElMessage.error(res.data.msg || '操作失败')
    }
  } catch (error) {
    reportRequestError('更新兑换商品状态', error)
  }
}

const deleteProduct = async (product) => {
  try {
    await ElMessageBox.confirm(
      `确定删除“${product.name}”吗？历史订单仍会保留商品快照。`,
      '删除兑换设备',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch (error) {
    return
  }

  try {
    const res = await request.delete(`/api/admin/exchange/product/${product.id}`)
    if (res.data.code === 200) {
      ElMessage.success('删除成功')
      if (products.value.length === 1 && productPage.value > 1) productPage.value -= 1
      await fetchProducts()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (error) {
    reportRequestError('删除兑换商品', error)
  }
}

const beforeProductImageUpload = (file) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('仅支持 JPG、PNG、GIF、WEBP、BMP 图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

const uploadProductImage = async ({ file, onSuccess, onError }) => {
  productImageUploading.value = true
  try {
    const payload = await uploadImageFile(file)

    productForm.imageUrl = payload.data.url
    onSuccess?.(payload)
    ElMessage.success('设备图片上传成功')
  } catch (error) {
    const message = error?.status === 413
      ? '图片过大，设备图片不能超过 5MB'
      : (error?.message || '设备图片上传失败，请稍后重试')
    ElMessage.error(message)
    onError?.(error)
  } finally {
    productImageUploading.value = false
  }
}

const openShipDialog = (order) => {
  shippingOrder.value = order
  Object.assign(shipForm, { expressCompany: '', expressNo: '', adminRemark: order.adminRemark || '' })
  shipDialogVisible.value = true
}

const confirmShip = async () => {
  const valid = await shipFormRef.value?.validate().catch(() => false)
  if (!valid || !shippingOrder.value) return

  orderSubmitting.value = true
  try {
    const res = await request.post(`/api/admin/exchange/order/${shippingOrder.value.id}/ship`, {
      expressCompany: shipForm.expressCompany.trim(),
      expressNo: shipForm.expressNo.trim(),
      adminRemark: shipForm.adminRemark.trim(),
    })
    if (res.data.code === 200) {
      ElMessage.success('发货成功')
      shipDialogVisible.value = false
      await Promise.all([fetchOrders(), fetchStatistics()])
    } else {
      ElMessage.error(res.data.msg || '发货失败')
    }
  } catch (error) {
    reportRequestError('提交兑换订单发货', error)
  } finally {
    orderSubmitting.value = false
  }
}

const advanceOrder = async (order, status) => {
  const targetText = orderStatusText(status)
  try {
    await ElMessageBox.confirm(
      `确定将订单 ${order.orderNo} 更新为“${targetText}”吗？`,
      '更新物流状态',
      { type: 'warning', confirmButtonText: '确认更新', cancelButtonText: '取消' },
    )
  } catch (error) {
    return
  }

  try {
    const res = await request.post(`/api/admin/exchange/order/${order.id}/updateStatus`, {
      status,
      description: status === 2 ? '包裹运输中' : '订单已到货',
    })
    if (res.data.code === 200) {
      ElMessage.success('状态更新成功')
      await Promise.all([fetchOrders(), fetchStatistics()])
    } else {
      ElMessage.error(res.data.msg || '状态更新失败')
    }
  } catch (error) {
    reportRequestError('更新兑换订单状态', error)
  }
}

const returnOrder = async (order) => {
  let reason
  try {
    const prompt = await ElMessageBox.prompt(
      `退回订单 ${order.orderNo} 后，将返还用户余额和库存。`,
      '退回兑换订单',
      {
        type: 'warning',
        inputType: 'textarea',
        inputPlaceholder: '请输入退回原因',
        inputValidator: (value) => value?.trim() ? true : '请输入退回原因',
        confirmButtonText: '确认退回',
        cancelButtonText: '取消',
      },
    )
    reason = prompt.value.trim()
  } catch (error) {
    return
  }

  try {
    const res = await request.post(`/api/admin/exchange/order/${order.id}/cancel`, { adminRemark: reason })
    if (res.data.code === 200) {
      ElMessage.success(res.data.msg || '订单已退回')
      await Promise.all([fetchOrders(), fetchStatistics(), fetchProducts()])
    } else {
      ElMessage.error(res.data.msg || '退回失败')
    }
  } catch (error) {
    reportRequestError('退回兑换订单', error)
  }
}

const viewOrder = async (order) => {
  currentOrder.value = order
  orderLogistics.value = []
  orderDetailVisible.value = true
  orderDetailLoading.value = true
  try {
    const res = await request.get(`/api/admin/exchange/order/${order.id}`)
    if (res.data.code === 200) {
      currentOrder.value = { ...order, ...(res.data.data?.order || {}) }
      orderLogistics.value = res.data.data?.logistics || []
    } else {
      ElMessage.error(res.data.msg || '获取订单详情失败')
    }
  } catch (error) {
    reportRequestError('获取兑换订单详情', error)
  } finally {
    orderDetailLoading.value = false
  }
}

const orderStatusText = (status) => orderStatusOptions.find((item) => item.value === Number(status))?.label || '未知'
const orderStatusType = (status) => ({ 0: 'warning', 1: 'primary', 2: 'info', 3: 'success', 4: 'danger' }[Number(status)] || 'info')
const levelName = (level) => Number(level) > 0 ? (levelNames.value[Number(level) - 1] || `等级${level}`) : '普通用户'
const formatMoney = (value) => Number(value || 0).toFixed(2)
const formatHashrate = (value) => Number(value || 0).toLocaleString('zh-CN')
const formatTime = (value) => value ? String(value).replace('T', ' ').substring(0, 19) : '-'

onMounted(() => {
  loadLevelSettings()
  fetchStatistics()
  fetchProducts()
})
</script>

<style scoped>
.exchange-page {
  width: 100%;
  max-width: 1680px;
  margin: 0 auto;
  color: var(--orin-text);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 92px;
  padding: 16px 18px;
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
}

.summary-icon {
  display: grid;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 6px;
  font-size: 22px;
}

.summary-icon.green { color: var(--orin-green-dark); background: var(--orin-green-soft); }
.summary-icon.graphite { color: var(--orin-text-soft); background: var(--orin-surface-soft); }
.summary-icon.amber { color: var(--orin-amber); background: rgba(166, 111, 18, 0.1); }
.summary-icon.teal { color: var(--orin-cyan); background: rgba(24, 127, 123, 0.1); }

.summary-item span {
  display: block;
  margin-bottom: 3px;
  color: var(--orin-muted);
  font-size: 12px;
}

.summary-item strong {
  color: var(--orin-text);
  font-size: 25px;
  line-height: 1;
}

.exchange-workspace {
  min-width: 0;
  padding: 0 16px 16px;
  overflow: hidden;
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
}

.workspace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 64px;
  border-bottom: 1px solid var(--orin-border-soft);
}

.view-tabs {
  width: 260px;
}

.view-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.view-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.view-tabs :deep(.el-tabs__content) {
  display: none;
}

.workspace-actions,
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.view-content {
  padding-top: 14px;
}

.filter-bar {
  margin-bottom: 14px;
  padding: 12px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.filter-bar :deep(.el-input) {
  width: min(360px, 34vw);
}

.filter-bar :deep(.el-select) {
  width: 160px;
}

.product-cell,
.order-product,
.user-cell,
.detail-product {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.product-thumb {
  flex: 0 0 56px;
  width: 56px;
  height: 56px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.image-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  color: var(--orin-dim);
  place-items: center;
  background: var(--orin-surface-soft);
  font-size: 20px;
}

.product-copy,
.order-number,
.order-amount,
.receiver-cell,
.logistics-cell,
.user-cell > div,
.order-product > div,
.detail-product > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 3px;
}

.product-copy strong,
.order-number strong,
.user-cell strong,
.order-product strong,
.receiver-cell strong,
.logistics-cell strong,
.detail-product strong {
  overflow: hidden;
  color: var(--orin-text);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-copy span,
.order-number span,
.user-cell span,
.order-product span,
.receiver-cell span,
.logistics-cell span,
.detail-product span,
.order-amount span {
  color: var(--orin-muted);
  font-size: 11px;
}

.product-copy small,
.receiver-cell small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--orin-muted);
  font-size: 11px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.money,
.order-amount strong {
  color: var(--orin-green-dark);
  font-variant-numeric: tabular-nums;
}

.level-price-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 12px;
  font-size: 11px;
}

.level-price-list span {
  min-width: 0;
  overflow: hidden;
  color: var(--orin-text-soft);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.level-price-list b {
  margin-right: 5px;
  color: var(--orin-muted);
  font-weight: 500;
}

.stock-value {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stock-value.empty {
  color: var(--orin-danger);
}

.order-product-image {
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  border: 1px solid var(--orin-border-soft);
  border-radius: 4px;
}

.receiver-cell small {
  max-width: 180px;
}

.muted {
  color: var(--orin-muted);
  font-size: 12px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.product-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.span-2 {
  grid-column: 1 / -1;
}

.product-form-grid :deep(.el-input-number),
.product-form-grid :deep(.el-select),
.ship-dialog :deep(.el-select) {
  width: 100%;
}

.image-editor {
  display: grid;
  grid-template-columns: 116px minmax(0, 1fr);
  align-items: end;
  gap: 12px;
  width: 100%;
}

.product-uploader :deep(.el-upload) {
  display: block;
  width: 116px;
  height: 92px;
  overflow: hidden;
  background: var(--orin-surface-soft);
  border: 1px dashed var(--orin-border-strong);
  border-radius: 5px;
}

.product-uploader img {
  display: block;
  width: 116px;
  height: 92px;
  object-fit: cover;
}

.upload-empty {
  display: flex;
  width: 116px;
  height: 92px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: var(--orin-muted);
  font-size: 12px;
}

.upload-empty .el-icon {
  color: var(--orin-green);
  font-size: 24px;
}

.order-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-product {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--orin-border-soft);
}

.detail-product-image {
  flex: 0 0 62px;
  width: 62px;
  height: 62px;
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.detail-product > div {
  flex: 1;
}

.logistics-history {
  padding-top: 2px;
}

.logistics-history h3 {
  margin: 0 0 14px;
  color: var(--orin-text);
  font-size: 14px;
}

.logistics-history :deep(.el-timeline-item__content) {
  display: flex;
  flex-direction: column;
  gap: 3px;
  color: var(--orin-text-soft);
  font-size: 12px;
}

.logistics-history :deep(.el-timeline-item__content span) {
  color: var(--orin-muted);
  font-size: 11px;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-head,
  .filter-bar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .view-tabs {
    flex: 1 1 260px;
  }
}

@media (max-width: 700px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .summary-item {
    min-height: 76px;
  }

  .exchange-workspace {
    padding: 0 10px 12px;
  }

  .workspace-actions,
  .filter-bar {
    width: 100%;
  }

  .workspace-actions .el-button,
  .filter-bar .el-button,
  .filter-bar :deep(.el-input),
  .filter-bar :deep(.el-select) {
    flex: 1 1 100%;
    width: 100%;
    margin-left: 0;
  }

  .product-form-grid {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }

  .image-editor {
    grid-template-columns: 1fr;
  }

  :deep(.el-dialog) {
    width: calc(100vw - 24px) !important;
    margin-top: 6vh;
  }

  :deep(.el-descriptions__body .el-descriptions__table) {
    table-layout: fixed;
  }
}
</style>
