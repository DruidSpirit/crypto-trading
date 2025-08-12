// Signals组件
const Signals = Vue.defineComponent({
    name: 'Signals',
    template: `
        <div class="tab-content">
            <div class="section">
                <div class="section-header">
                    <h2>
                        <i class="fas fa-list"></i>
                        信号列表
                    </h2>
                    <button class="btn btn-primary" @click="refreshSignals">
                        <i class="fas fa-sync-alt" :class="{ 'fa-spin': loading }"></i>
                        刷新
                    </button>
                </div>

                <!-- Desktop Filters -->
                <div class="desktop-filters">
                    <div class="filters-grid">
                        <div class="filter-group">
                            <label>搜索交易对</label>
                            <input type="text" 
                                   v-model="filter.search" 
                                   @input="debounceFilter" 
                                   placeholder="输入交易对名称..." 
                                   class="form-control">
                        </div>
                        <div class="filter-group">
                            <label>信号类型</label>
                            <select v-model="filter.signalType" @change="applyFilter" class="form-control">
                                <option value="">全部类型</option>
                                <option value="BUY">买入</option>
                                <option value="SELL">卖出</option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>策略名称</label>
                            <select v-model="filter.strategy" @change="applyFilter" class="form-control">
                                <option value="">全部策略</option>
                                <option v-for="strategy in selectOptions.strategyNames" :key="strategy" :value="strategy">
                                    {{ strategy }}
                                </option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>交易所</label>
                            <select v-model="filter.exchange" @change="applyFilter" class="form-control">
                                <option value="">全部交易所</option>
                                <option v-for="exchange in selectOptions.exchangeTypes" :key="exchange" :value="exchange">
                                    {{ exchange }}
                                </option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>开始日期</label>
                            <input type="date" 
                                   v-model="filter.startDate" 
                                   @change="applyFilter" 
                                   class="form-control">
                        </div>
                        <div class="filter-group">
                            <label>结束日期</label>
                            <input type="date" 
                                   v-model="filter.endDate" 
                                   @change="applyFilter" 
                                   class="form-control">
                        </div>
                    </div>
                    <div class="filters-actions">
                        <button class="btn btn-secondary" @click="resetFilter">
                            <i class="fas fa-undo"></i>
                            重置筛选
                        </button>
                        <div v-if="hasActiveFilters" class="active-filters">
                            <span class="filter-count">已应用 {{ activeFilterCount }} 个筛选条件</span>
                        </div>
                    </div>
                </div>

                <!-- Mobile Filters -->
                <div class="mobile-filters">
                    <button class="mobile-filter-toggle" @click="toggleMobileFilter" :class="{ 'expanded': mobileFilterExpanded }">
                        <i class="fas fa-filter"></i>
                        <span>筛选条件</span>
                        <span v-if="hasActiveFilters" class="filter-badge">{{ activeFilterCount }}</span>
                        <i class="fas fa-chevron-down toggle-icon"></i>
                    </button>
                    <div class="mobile-filter-content" :class="{ 'expanded': mobileFilterExpanded }">
                        <div class="mobile-filter-grid">
                            <div class="filter-group">
                                <label>搜索交易对</label>
                                <input type="text" 
                                       v-model="filter.search" 
                                       @input="debounceFilter" 
                                       placeholder="输入交易对名称..." 
                                       class="form-control">
                            </div>
                            <div class="filter-group">
                                <label>信号类型</label>
                                <select v-model="filter.signalType" @change="applyFilter" class="form-control">
                                    <option value="">全部类型</option>
                                    <option value="BUY">买入</option>
                                    <option value="SELL">卖出</option>
                                </select>
                            </div>
                            <div class="filter-group">
                                <label>策略名称</label>
                                <select v-model="filter.strategy" @change="applyFilter" class="form-control">
                                    <option value="">全部策略</option>
                                    <option v-for="strategy in selectOptions.strategyNames" :key="strategy" :value="strategy">
                                        {{ strategy }}
                                    </option>
                                </select>
                            </div>
                            <div class="filter-group">
                                <label>交易所</label>
                                <select v-model="filter.exchange" @change="applyFilter" class="form-control">
                                    <option value="">全部交易所</option>
                                    <option v-for="exchange in selectOptions.exchangeTypes" :key="exchange" :value="exchange">
                                        {{ exchange }}
                                    </option>
                                </select>
                            </div>
                            <div class="filter-group">
                                <label>开始日期</label>
                                <input type="date" 
                                       v-model="filter.startDate" 
                                       @change="applyFilter" 
                                       class="form-control">
                            </div>
                            <div class="filter-group">
                                <label>结束日期</label>
                                <input type="date" 
                                       v-model="filter.endDate" 
                                       @change="applyFilter" 
                                       class="form-control">
                            </div>
                        </div>
                        <div class="mobile-filter-actions">
                            <button class="btn btn-secondary btn-sm" @click="resetFilter">
                                <i class="fas fa-undo"></i>
                                重置
                            </button>
                        </div>
                    </div>
                </div>

                <!-- Desktop Table -->
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>时间</th>
                                <th>交易对</th>
                                <th>信号类型</th>
                                <th>价格</th>
                                <th>策略</th>
                                <th>交易所</th>
                                <th>盈亏比</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="signal in signals" :key="signal.id">
                                <td>{{ formattedSignalTime(signal.signalTime) }}</td>
                                <td>
                                    <span class="crypto-symbol">{{ signal.symbol }}</span>
                                </td>
                                <td>
                                    <span :class="['signal-badge', signal.signal.toLowerCase()]">
                                        <i :class="signal.signal === 'BUY' ? 'fas fa-arrow-up' : 'fas fa-arrow-down'"></i>
                                        {{ signal.signal === 'BUY' ? '买入' : '卖出' }}
                                    </span>
                                </td>
                                <td class="price">{{ signal.price }}</td>
                                <td>{{ signal.strategy }}</td>
                                <td>{{ signal.exchange }}</td>
                                <td>{{ formatProfitLossRatio(signal.profitLossRatio) }}</td>
                                <td>
                                    <button class="btn btn-sm btn-outline" @click="showDetails(signal)">
                                        <i class="fas fa-eye"></i>
                                        详情
                                    </button>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <!-- Mobile Cards -->
                <div class="mobile-signal-cards">
                    <div v-for="signal in signals" :key="signal.id" class="signal-card" @click="showDetails(signal)">
                        <div class="signal-card-header">
                            <span class="crypto-symbol">{{ signal.symbol }}</span>
                            <span :class="['signal-badge', signal.signal.toLowerCase()]">
                                <i :class="signal.signal === 'BUY' ? 'fas fa-arrow-up' : 'fas fa-arrow-down'"></i>
                                {{ signal.signal === 'BUY' ? '买入' : '卖出' }}
                            </span>
                        </div>
                        <div class="signal-card-body">
                            <div class="signal-price">{{ signal.price }}</div>
                            <div class="signal-meta">
                                <span class="signal-time">{{ formattedSignalTime(signal.signalTime) }}</span>
                                <span class="signal-exchange">{{ signal.exchange }}</span>
                            </div>
                            <div class="signal-strategy">{{ signal.strategy }}</div>
                            <div class="signal-ratio">盈亏比: {{ formatProfitLossRatio(signal.profitLossRatio) }}</div>
                        </div>
                    </div>
                </div>

                <!-- Pagination -->
                <div class="pagination" v-if="totalPages > 1">
                    <button class="btn btn-pagination" @click="prevPage" :disabled="currentPage === 1">
                        <i class="fas fa-chevron-left"></i>
                    </button>
                    
                    <button v-for="page in visiblePages" 
                            :key="page" 
                            :class="['btn', 'btn-pagination', { 'active': page === currentPage }]" 
                            @click="changePage(page)">
                        {{ page }}
                    </button>
                    
                    <button class="btn btn-pagination" @click="nextPage" :disabled="currentPage === totalPages">
                        <i class="fas fa-chevron-right"></i>
                    </button>
                </div>
            </div>
        </div>
    `,
    inject: [
        'filter', 'signals', 'selectOptions', 'loading', 'hasActiveFilters', 'activeFilterCount', 
        'mobileFilterExpanded', 'totalPages', 'currentPage', 'visiblePages',
        'debounceFilter', 'applyFilter', 'resetFilter', 'refreshSignals', 'toggleMobileFilter',
        'prevPage', 'nextPage', 'changePage', 'showDetails', 'formattedSignalTime', 'formatProfitLossRatio'
    ]
});