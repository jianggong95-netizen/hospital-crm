<template>
  <div class="not-found">
    <div class="card panel">
      <span class="code">404</span>
      <h1>页面不存在</h1>
      <p>请检查地址是否正确，或返回工作台继续操作。</p>
      <RouterLink to="/">
        <el-button type="primary">返回工作台</el-button>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  background: var(--crm-bg);
}

.panel {
  padding: 48px 56px;
  text-align: center;
}

.code {
  color: var(--crm-primary);
  font-size: 64px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

h1 {
  margin: 8px 0;
  font-size: 22px;
}

p {
  margin-bottom: 24px;
  color: var(--crm-muted);
}
</style>
