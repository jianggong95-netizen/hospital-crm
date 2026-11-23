<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  console.info('Hospital CRM mobile launched')
})
</script>

<style lang="scss">
page {
  background: #f4f7fb;
  color: #17233b;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 28rpx;
  line-height: 1.6;
}

.page {
  min-height: 100vh;
  padding: 24rpx 28rpx 60rpx;
  box-sizing: border-box;
}

.card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(31, 78, 121, 0.06);
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #17233b;
  margin-bottom: 16rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.muted {
  color: #8a94a6;
  font-size: 24rpx;
}

.field {
  margin-bottom: 22rpx;
}

.field-label {
  display: block;
  font-size: 26rpx;
  color: #5b6679;
  margin-bottom: 10rpx;
}

.input,
.picker-box {
  width: 100%;
  box-sizing: border-box;
  height: 84rpx;
  line-height: 84rpx;
  padding: 0 22rpx;
  background: #f6f8fc;
  border: 1rpx solid #e3e9f3;
  border-radius: 14rpx;
  font-size: 28rpx;
  color: #17233b;
}

.picker-box.empty {
  color: #a7b0c0;
}

.btn-primary {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 14rpx;
  background: linear-gradient(135deg, #2d6dad, #4c8fd8);
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
  border: none;
}

.btn-primary[disabled] {
  opacity: 0.6;
}

.btn-ghost {
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  border: 1rpx solid #cfd9e8;
  color: #2d6dad;
  font-size: 24rpx;
  background: #fff;
}

.empty-block {
  padding: 60rpx 0;
  text-align: center;
  color: #9aa4b5;
  font-size: 26rpx;
}

.avatar-chip {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  padding: 10rpx 20rpx;
  background: #eef4fc;
  border-radius: 999rpx;
  color: #2d6dad;
  font-size: 24rpx;
}
</style>
