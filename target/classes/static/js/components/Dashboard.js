// Dashboard组件模板
const DashboardTemplate = `
        <div class="tab-content">
            <!-- Dashboard Stats -->
            <div class="dashboard-grid">
                <div class="stat-card">
                    <div class="stat-card-header">
                        <div class="stat-icon primary">
                            <i class="fas fa-signal"></i>
                        </div>
                    </div>
                    <div class="stat-value">{{ dashboardStats.totalSignals }}</div>
                    <div class="stat-label">总信号数</div>
                    <div class="stat-change positive">
                        <i class="fas fa-arrow-up"></i>
                        <span>+12%</span>
                        <span>较昨日</span>
                    </div>
                </div>

                <div class="stat-card">
                    <div class="stat-card-header">
                        <div class="stat-icon success">
                            <i class="fas fa-chart-line"></i>
                        </div>
                    </div>
                    <div class="stat-value">{{ dashboardStats.buySignals }}</div>
                    <div class="stat-label">买入信号</div>
                    <div class="stat-change positive">
                        <i class="fas fa-arrow-up"></i>
                        <span>+8%</span>
                        <span>较昨日</span>
                    </div>
                </div>

                <div class="stat-card">
                    <div class="stat-card-header">
                        <div class="stat-icon danger">
                            <i class="fas fa-chart-line-down"></i>
                        </div>
                    </div>
                    <div class="stat-value">{{ dashboardStats.sellSignals }}</div>
                    <div class="stat-label">卖出信号</div>
                    <div class="stat-change negative">
                        <i class="fas fa-arrow-down"></i>
                        <span>-3%</span>
                        <span>较昨日</span>
                    </div>
                </div>

                <div class="stat-card">
                    <div class="stat-card-header">
                        <div class="stat-icon warning">
                            <i class="fas fa-exchange-alt"></i>
                        </div>
                    </div>
                    <div class="stat-value">{{ dashboardStats.activePairs }}</div>
                    <div class="stat-label">活跃交易对</div>
                    <div class="stat-change positive">
                        <i class="fas fa-arrow-up"></i>
                        <span>+5%</span>
                        <span>较昨日</span>
                    </div>
                </div>
            </div>

            <!-- Chart Section -->
            <div class="section">
                <div class="section-header">
                    <h2>
                        <i class="fas fa-chart-area"></i>
                        信号趋势
                    </h2>
                </div>
                <div class="chart-container">
                    <canvas id="signalChart"></canvas>
                </div>
            </div>

            <!-- Latest Signals -->
            <div class="section">
                <div class="section-header">
                    <h2>
                        <i class="fas fa-clock"></i>
                        最新信号
                    </h2>
                    <button class="btn btn-primary" @click="refreshSignals">
                        <i class="fas fa-sync-alt" :class="{ 'fa-spin': loading }"></i>
                        刷新
                    </button>
                </div>

                <!-- Desktop Table -->
                <div class="dashboard-table">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>时间</th>
                                <th>交易对</th>
                                <th>信号类型</th>
                                <th>价格</th>
                                <th>策略</th>
                                <th>交易所</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="signal in dashboardSignals" :key="signal.id">
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
                <div class="dashboard-cards">
                    <div v-for="signal in dashboardSignals" :key="signal.id" class="signal-card" @click="showDetails(signal)">
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
                        </div>
                    </div>
                </div>
            </div>
        </div>
`;