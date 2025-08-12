// Backtest组件
const Backtest = Vue.defineComponent({
    name: 'Backtest',
    template: `
        <div class="tab-content">
            <div class="section">
                <div class="section-header">
                    <h2>
                        <i class="fas fa-chart-bar"></i>
                        策略回测
                    </h2>
                </div>

                <!-- Data Management -->
                <div class="backtest-section">
                    <h3>数据管理</h3>
                    <div class="backtest-form">
                        <div class="form-row">
                            <div class="form-group">
                                <label>交易对 (逗号分隔)</label>
                                <input type="text" 
                                       v-model="backtest.downloadSymbols" 
                                       class="form-control" 
                                       placeholder="BTCUSDT,ETHUSDT">
                            </div>
                            <div class="form-group">
                                <label>开始日期</label>
                                <input type="date" 
                                       v-model="backtest.downloadStartDate" 
                                       class="form-control">
                            </div>
                            <div class="form-group">
                                <label>结束日期</label>
                                <input type="date" 
                                       v-model="backtest.downloadEndDate" 
                                       class="form-control">
                            </div>
                            <div class="form-group">
                                <button class="btn btn-primary" 
                                        @click="batchDownloadData" 
                                        :disabled="backtest.downloading">
                                    <i class="fas fa-download" :class="{ 'fa-spin': backtest.downloading }"></i>
                                    {{ backtest.downloading ? '下载中...' : '批量下载' }}
                                </button>
                            </div>
                        </div>
                    </div>
                    
                    <!-- Data Info -->
                    <div v-if="backtest.dataInfo" class="data-info">
                        <h4>当前数据概览</h4>
                        <div class="info-grid">
                            <div class="info-item">
                                <span class="label">可用交易对:</span>
                                <span class="value">{{ backtest.dataInfo.available_symbols?.length || 0 }}</span>
                            </div>
                            <div class="info-item">
                                <span class="label">数据时间范围:</span>
                                <span class="value">{{ backtest.dataInfo.date_range || '无数据' }}</span>
                            </div>
                            <div class="info-item">
                                <span class="label">总记录数:</span>
                                <span class="value">{{ backtest.dataInfo.total_records || 0 }}</span>
                            </div>
                        </div>
                        <button class="btn btn-secondary btn-sm" @click="getDataInfo">
                            <i class="fas fa-sync-alt"></i>
                            刷新信息
                        </button>
                    </div>
                </div>

                <!-- Backtest Configuration -->
                <div class="backtest-section">
                    <h3>回测配置</h3>
                    <div class="backtest-form">
                        <div class="form-row">
                            <div class="form-group">
                                <label>策略名称</label>
                                <input type="text" 
                                       v-model="backtest.strategyName" 
                                       class="form-control" 
                                       placeholder="输入策略名称">
                            </div>
                            <div class="form-group">
                                <label>交易对</label>
                                <select v-model="backtest.symbol" class="form-control">
                                    <option value="BTCUSDT">BTCUSDT</option>
                                    <option value="ETHUSDT">ETHUSDT</option>
                                    <option value="BNBUSDT">BNBUSDT</option>
                                    <option value="ADAUSDT">ADAUSDT</option>
                                    <option value="SOLUSDT">SOLUSDT</option>
                                </select>
                            </div>
                            <div class="form-group">
                                <label>时间周期</label>
                                <select v-model="backtest.timeframe" class="form-control">
                                    <option value="1m">1分钟</option>
                                    <option value="5m">5分钟</option>
                                    <option value="15m">15分钟</option>
                                    <option value="30m">30分钟</option>
                                    <option value="1h">1小时</option>
                                    <option value="4h">4小时</option>
                                    <option value="1d">1天</option>
                                </select>
                            </div>
                        </div>
                        <div class="form-row">
                            <div class="form-group">
                                <label>开始日期</label>
                                <input type="date" 
                                       v-model="backtest.startDate" 
                                       class="form-control">
                            </div>
                            <div class="form-group">
                                <label>结束日期</label>
                                <input type="date" 
                                       v-model="backtest.endDate" 
                                       class="form-control">
                            </div>
                            <div class="form-group">
                                <label>初始资金</label>
                                <input type="number" 
                                       v-model="backtest.initialBalance" 
                                       class="form-control" 
                                       min="1000" 
                                       step="100">
                            </div>
                        </div>
                        <div class="form-actions">
                            <button class="btn btn-primary" 
                                    @click="runBacktest" 
                                    :disabled="backtest.running">
                                <i class="fas fa-play" :class="{ 'fa-spin': backtest.running }"></i>
                                {{ backtest.running ? '运行中...' : '开始回测' }}
                            </button>
                            <button class="btn btn-secondary" @click="resetBacktestForm">
                                <i class="fas fa-undo"></i>
                                重置
                            </button>
                        </div>
                    </div>
                </div>

                <!-- Backtest Results -->
                <div v-if="backtest.results" class="backtest-section">
                    <h3>回测结果</h3>
                    <div class="results-grid">
                        <div class="result-card">
                            <div class="result-label">总收益率</div>
                            <div class="result-value" :class="{ 'positive': backtest.results.total_return >= 0, 'negative': backtest.results.total_return < 0 }">
                                {{ (backtest.results.total_return * 100).toFixed(2) }}%
                            </div>
                        </div>
                        <div class="result-card">
                            <div class="result-label">最终余额</div>
                            <div class="result-value">{{ backtest.results.final_balance?.toFixed(2) || 0 }}</div>
                        </div>
                        <div class="result-card">
                            <div class="result-label">交易次数</div>
                            <div class="result-value">{{ backtest.results.total_trades || 0 }}</div>
                        </div>
                        <div class="result-card">
                            <div class="result-label">胜率</div>
                            <div class="result-value">{{ (backtest.results.win_rate * 100).toFixed(2) }}%</div>
                        </div>
                        <div class="result-card">
                            <div class="result-label">最大回撤</div>
                            <div class="result-value negative">{{ (backtest.results.max_drawdown * 100).toFixed(2) }}%</div>
                        </div>
                        <div class="result-card">
                            <div class="result-label">夏普比率</div>
                            <div class="result-value">{{ backtest.results.sharpe_ratio?.toFixed(3) || 'N/A' }}</div>
                        </div>
                    </div>

                    <!-- Trade Records -->
                    <div v-if="backtest.results.trades && backtest.results.trades.length > 0" class="trades-section">
                        <h4>交易记录</h4>
                        <div class="trades-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>开仓时间</th>
                                        <th>平仓时间</th>
                                        <th>类型</th>
                                        <th>开仓价</th>
                                        <th>平仓价</th>
                                        <th>数量</th>
                                        <th>收益</th>
                                        <th>收益率</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr v-for="trade in backtest.results.trades.slice(0, 20)" :key="trade.entry_time">
                                        <td>{{ new Date(trade.entry_time).toLocaleString() }}</td>
                                        <td>{{ new Date(trade.exit_time).toLocaleString() }}</td>
                                        <td>
                                            <span :class="['signal-badge', trade.side.toLowerCase()]">
                                                {{ trade.side === 'BUY' ? '买入' : '卖出' }}
                                            </span>
                                        </td>
                                        <td>{{ trade.entry_price?.toFixed(4) || 'N/A' }}</td>
                                        <td>{{ trade.exit_price?.toFixed(4) || 'N/A' }}</td>
                                        <td>{{ trade.quantity?.toFixed(4) || 'N/A' }}</td>
                                        <td :class="{ 'positive': trade.pnl >= 0, 'negative': trade.pnl < 0 }">
                                            {{ trade.pnl?.toFixed(2) || 'N/A' }}
                                        </td>
                                        <td :class="{ 'positive': trade.return >= 0, 'negative': trade.return < 0 }">
                                            {{ (trade.return * 100).toFixed(2) }}%
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                        <p v-if="backtest.results.trades.length > 20" class="text-muted">
                            显示前20条记录，共{{ backtest.results.trades.length }}条交易记录
                        </p>
                    </div>
                </div>
            </div>
        </div>
    `,
    inject: [
        'backtest', 'batchDownloadData', 'getDataInfo', 'runBacktest', 'resetBacktestForm'
    ]
});