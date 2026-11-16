<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="forbidden card">
    <span class="badge badge-danger">403</span>
    <h2>无访问权限</h2>
    <p>当前账号未获得访问该页面的权限。如确有业务需要，请联系系统管理员调整角色或数据范围。</p>
    <div class="actions">
      <el-button type="primary" @click="router.push('/')">返回工作台</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.forbidden {
  max-width: 560px;
  margin: 48px auto;
  padding: 40px 36px;
  text-align: center;
}

h2 {
  margin-top: 16px;
  font-size: 24px;
}

p {
  margin-top: 10px;
  color: var(--crm-ink-soft);
  font-size: 13.5px;
  line-height: 1.8;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 26px;
}
</style>
