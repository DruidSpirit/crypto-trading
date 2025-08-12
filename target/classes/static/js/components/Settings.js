// Settings组件
const Settings = Vue.defineComponent({
    name: 'Settings',
    template: `
        <div class="tab-content">
            <div class="section">
                <div class="section-header">
                    <h2>
                        <i class="fas fa-cog"></i>
                        系统设置
                    </h2>
                </div>

                <div class="settings-container">
                    <!-- 加密货币设置 -->
                    <div class="settings-group">
                        <h3>
                            <i class="fas fa-coins"></i>
                            加密货币设置
                        </h3>
                        <div class="form-group">
                            <label>模式选择</label>
                            <select v-model="settings.cryptoMode" class="form-control" @change="updateMinFetchFrequency">
                                <option value="custom">自定义币种</option>
                                <option value="all">所有币种</option>
                            </select>
                        </div>
                        <div v-if="settings.cryptoMode === 'custom'" class="form-group">
                            <label>自定义加密货币列表</label>
                            <div class="crypto-list">
                                <div v-for="(crypto, index) in settings.cryptoSymbols" :key="index" class="crypto-item">
                                    <span>{{ crypto }}</span>
                                    <button @click="removeCrypto(index)" class="btn btn-sm btn-danger">
                                        <i class="fas fa-times"></i>
                                    </button>
                                </div>
                            </div>
                            <div class="add-crypto">
                                <input v-model="newCrypto" 
                                       @keyup.enter="addCrypto" 
                                       type="text" 
                                       placeholder="输入币种符号，如BTC" 
                                       class="form-control">
                                <button @click="addCrypto" class="btn btn-primary">
                                    <i class="fas fa-plus"></i>
                                    添加
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- 交易所设置 -->
                    <div class="settings-group">
                        <h3>
                            <i class="fas fa-exchange-alt"></i>
                            交易所设置
                        </h3>
                        <div class="form-group">
                            <label>支持的交易所</label>
                            <div class="exchange-list">
                                <div v-for="(exchange, index) in settings.exchangeTypes" :key="index" class="exchange-item">
                                    <span>{{ exchange }}</span>
                                    <button @click="removeExchange(index)" 
                                            class="btn btn-sm btn-danger" 
                                            :disabled="settings.exchangeTypes.length === 1">
                                        <i class="fas fa-times"></i>
                                    </button>
                                </div>
                            </div>
                            <div class="add-exchange">
                                <select v-model="newExchange" class="form-control">
                                    <option value="">选择交易所</option>
                                    <option v-for="exchange in availableExchanges" :key="exchange" :value="exchange">
                                        {{ exchange }}
                                    </option>
                                </select>
                                <button @click="addExchange" class="btn btn-primary" :disabled="!newExchange">
                                    <i class="fas fa-plus"></i>
                                    添加
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- 抓取频率设置 -->
                    <div class="settings-group">
                        <h3>
                            <i class="fas fa-clock"></i>
                            抓取频率设置
                        </h3>
                        <div class="form-group">
                            <label>抓取频率 (分钟)</label>
                            <input v-model.number="settings.fetchFrequency" 
                                   @input="validateFetchFrequency"
                                   type="number" 
                                   :min="minFetchFrequency" 
                                   step="1" 
                                   class="form-control">
                            <small class="form-help">
                                最小频率: {{ minFetchFrequency }} 分钟 (根据币种数量和代理数量计算)
                            </small>
                        </div>
                    </div>

                    <!-- 代理设置 -->
                    <div class="settings-group">
                        <h3>
                            <i class="fas fa-shield-alt"></i>
                            代理设置
                        </h3>
                        <div class="form-group">
                            <label>代理列表 (可选)</label>
                            <div v-if="settings.proxies && settings.proxies.length > 0" class="proxy-list">
                                <div v-for="(proxy, index) in settings.proxies" :key="index" class="proxy-item">
                                    <span>{{ proxy }}</span>
                                    <button @click="settings.proxies.splice(index, 1)" class="btn btn-sm btn-danger">
                                        <i class="fas fa-times"></i>
                                    </button>
                                </div>
                            </div>
                            <div class="proxy-info">
                                <p class="text-muted">
                                    <i class="fas fa-info-circle"></i>
                                    代理格式: http://ip:port 或 socks5://ip:port
                                </p>
                                <p class="text-muted">留空表示不使用代理</p>
                            </div>
                        </div>
                    </div>

                    <!-- 保存按钮 -->
                    <div class="settings-actions">
                        <button @click="saveSettings" class="btn btn-primary btn-lg">
                            <i class="fas fa-save"></i>
                            保存设置
                        </button>
                        <button @click="loadSettings" class="btn btn-secondary">
                            <i class="fas fa-undo"></i>
                            重置
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `,
    inject: [
        'settings', 'newCrypto', 'newExchange', 'minFetchFrequency', 'availableExchanges',
        'addCrypto', 'removeCrypto', 'addExchange', 'removeExchange', 'updateMinFetchFrequency',
        'validateFetchFrequency', 'saveSettings', 'loadSettings'
    ]
});