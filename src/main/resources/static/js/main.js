console.log('=== main.js开始加载 ===');
console.log('Vue:', Vue);

const { createApp } = Vue;
const ITEMS_PER_PAGE = 10;
const MAX_VISIBLE_PAGES = 5;

console.log('=== 开始创建Vue应用 ===');

createApp({
    data() {
        return {
            isDarkTheme: true,
            themeMode: 'auto', // auto, dark, light
            loading: false,
            showSettings: false,
            showDetailsModal: false,
            currentTab: 'dashboard', // dashboard, signals, strategies, backtest, settings
            settings: {
                cryptoMode: 'custom',
                cryptoSymbols: [],
                exchangeTypes: [],
                fetchFrequency: 15,
                proxies: []
            },
            originalSettings: null,
            filter: {
                search: '',
                signalType: '',
                strategy: '',
                exchange: '',
                startDate: '',
                endDate: ''
            },
            signals: [],
            dashboardSignals: [], // 首页独立的最新信号数据
            totalPages: 1,
            currentPage: 1,
            maxVisiblePages: MAX_VISIBLE_PAGES,
            selectedSignal: null,
            selectOptions: {
                exchangeTypes: [],
                strategyNames: [],
                defaultCryptoCoinSymbols: []
            },
            newCrypto: '',
            newExchange: '',
            minFetchFrequency: 5,
            dashboardStats: {
                totalSignals: 0,
                buySignals: 0,
                sellSignals: 0,
                activePairs: 0
            },
            signalChart: null,
            filterTimeout: null,
            filterToggleTimeout: null,
            mobileFilterExpanded: false,
            
            // 策略管理相关数据
            strategies: [],
            showUploadStrategyModal: false,
            showDeleteConfirmModal: false,
            selectedFile: null,
            strategyDescription: '',
            uploading: false,
            deleting: false,
            strategyToDelete: null,
            
            // 回测相关数据
            backtest: {
                
                strategyName: '',
                symbol: 'BTCUSDT',
                startDate: '',
                endDate: '',
                initialBalance: 10000,
                running: false,
                results: null
            }
        };
    },
    computed: {
        visiblePages() {
            const half = Math.floor(this.maxVisiblePages / 2);
            let start = Math.max(1, this.currentPage - half);
            let end = Math.min(this.totalPages, start + this.maxVisiblePages - 1);
            if (end - start + 1 < this.maxVisiblePages) {
                start = Math.max(1, end - this.maxVisiblePages + 1);
            }
            return Array.from({ length: end - start + 1 }, (_, i) => start + i);
        },
        availableExchanges() {
            return this.selectOptions.exchangeTypes.filter(
                exchange => !this.settings.exchangeTypes.includes(exchange)
            );
        },
        hasActiveFilters() {
            return this.filter.search || 
                   this.filter.signalType || 
                   this.filter.strategy || 
                   this.filter.exchange || 
                   this.filter.startDate || 
                   this.filter.endDate;
        },
        activeFilterCount() {
            let count = 0;
            if (this.filter.search) count++;
            if (this.filter.signalType) count++;
            if (this.filter.strategy) count++;
            if (this.filter.exchange) count++;
            if (this.filter.startDate) count++;
            if (this.filter.endDate) count++;
            return count;
        }
    },
    mounted() {
        console.log('=== Vue应用已挂载 ===');
        console.log('开始初始化应用组件...');
        
        this.loadSelectOptions();
        this.fetchSignals();
        this.loadDashboardStats();
        this.loadDashboardSignals();
        this.initChart();
        this.initTheme();
        this.loadSettings();
        
        console.log('调用loadStrategies...');
        this.loadStrategies();
        
        this.initBacktestDates();

        // 每分钟检查一次时间并更新主题
        setInterval(() => {
            if (this.themeMode === 'auto') {
                this.updateThemeByTime();
            }
        }, 60000);
    },
    methods: {
        toggleTheme() {
            // 循环切换: auto -> dark -> light -> auto
            if (this.themeMode === 'auto') {
                this.themeMode = 'dark';
                this.isDarkTheme = true;
            } else if (this.themeMode === 'dark') {
                this.themeMode = 'light';
                this.isDarkTheme = false;
            } else {
                this.themeMode = 'auto';
                this.updateThemeByTime();
            }

            this.saveThemePreference();
            this.applyTheme();
        },

        initTheme() {
            // 从localStorage加载主题偏好
            const savedMode = localStorage.getItem('themeMode') || 'auto';
            this.themeMode = savedMode;

            if (this.themeMode === 'auto') {
                this.updateThemeByTime();
            } else {
                this.isDarkTheme = this.themeMode === 'dark';
            }

            this.applyTheme();
        },

        updateThemeByTime() {
            const now = new Date();
            const hour = now.getHours();

            // 6点到18点为白天，18点到次日6点为夜晚
            this.isDarkTheme = hour < 6 || hour >= 18;
        },

        applyTheme() {
            document.body.classList.toggle('theme-light', !this.isDarkTheme);
        },

        saveThemePreference() {
            localStorage.setItem('themeMode', this.themeMode);
        },

        getThemeIcon() {
            if (this.themeMode === 'auto') {
                return 'fas fa-clock';
            } else if (this.isDarkTheme) {
                return 'fas fa-sun';
            } else {
                return 'fas fa-moon';
            }
        },

        getThemeTooltip() {
            if (this.themeMode === 'auto') {
                return '自动主题 (根据时间)';
            } else if (this.isDarkTheme) {
                return '暗色主题';
            } else {
                return '亮色主题';
            }
        },

        switchTab(tab) {
            this.currentTab = tab;

            // 切换到设置页面时加载设置
            if (tab === 'settings') {
                this.loadSettings();
            }

            // 切换到信号列表时重置筛选
            if (tab === 'signals') {
                this.resetFilter();
            }
        },

        showToast(message, type = 'success') {
            const toast = document.getElementById('toast');
            const toastMessage = document.getElementById('toast-message');

            toastMessage.textContent = message;
            toast.className = `toast ${type}`;
            toast.classList.add('show');

            setTimeout(() => {
                toast.classList.remove('show');
            }, 3000);
        },

        debounceFilter() {
            if (this.filterTimeout) {
                clearTimeout(this.filterTimeout);
            }
            this.filterTimeout = setTimeout(() => {
                this.applyFilter();
            }, 500);
        },


        loadSettings() {
            ApiService.getSettings()
                .then(response => {
                    const settings = response.data || {};
                    this.settings = {
                        cryptoMode: settings.cryptoMode || 'custom',
                        cryptoSymbols: settings.cryptoSymbols || this.selectOptions.defaultCryptoCoinSymbols.slice(0, 10),
                        exchangeTypes: settings.exchangeTypes || ['GATE_IO'],
                        fetchFrequency: settings.fetchFrequency || 15,
                        proxies: settings.proxies || []
                    };
                    this.updateMinFetchFrequency();
                    this.originalSettings = JSON.parse(JSON.stringify(this.settings));
                })
                .catch(error => {
                    console.error('加载设置失败:', error);
                    this.showToast('加载设置失败', 'error');
                });
        },

        saveSettings() {
            if (this.settings.fetchFrequency < this.minFetchFrequency) {
                this.settings.fetchFrequency = this.minFetchFrequency;
                this.showToast(`频率已调整为最小值 ${this.minFetchFrequency} 分钟`, 'warning');
            }

            ApiService.saveSettings(this.settings)
                .then(() => {
                    this.showToast('设置保存成功！');
                    this.originalSettings = JSON.parse(JSON.stringify(this.settings));
                })
                .catch(error => {
                    console.error('保存设置失败:', error);
                    this.showToast('保存设置失败', 'error');
                });
        },

        addCrypto() {
            if (this.newCrypto && this.newCrypto.trim()) {
                const crypto = this.newCrypto.trim().toUpperCase();
                if (!this.settings.cryptoSymbols.includes(crypto)) {
                    this.settings.cryptoSymbols.push(crypto);
                    this.newCrypto = '';
                    this.updateMinFetchFrequency();
                }
            }
        },

        removeCrypto(index) {
            this.settings.cryptoSymbols.splice(index, 1);
            this.updateMinFetchFrequency();
        },

        addExchange() {
            if (this.newExchange && !this.settings.exchangeTypes.includes(this.newExchange)) {
                this.settings.exchangeTypes.push(this.newExchange);
                this.newExchange = '';
            }
        },

        removeExchange(index) {
            if (this.settings.exchangeTypes.length > 1) {
                this.settings.exchangeTypes.splice(index, 1);
            } else {
                this.showToast('至少需要保留一个交易所', 'warning');
            }
        },

        updateMinFetchFrequency() {
            const baseTimePerCoin = 20;
            let coinCount = this.settings.cryptoMode === 'custom'
                ? this.settings.cryptoSymbols.length
                : 2000;
            const proxyCount = Math.max(1, this.settings.proxies.length);
            let totalMinutes = Math.ceil(coinCount * baseTimePerCoin / proxyCount / 60);
            this.minFetchFrequency = Math.max(totalMinutes, 5);
        },

        validateFetchFrequency() {
            if (this.settings.fetchFrequency < this.minFetchFrequency) {
                this.settings.fetchFrequency = this.minFetchFrequency;
            }
        },

        loadSelectOptions() {
            ApiService.getSelectOptions()
                .then(response => {
                    this.selectOptions = response.data;
                })
                .catch(error => console.error('加载选项失败:', error));
        },

        loadDashboardStats() {
            ApiService.getDashboardStats()
                .then(response => {
                    this.dashboardStats = response.data;
                })
                .catch(error => {
                    console.error('获取统计数据失败:', error);
                    // 使用默认数据
                    this.dashboardStats = {
                        totalSignals: this.signals.length || 156,
                        buySignals: Math.floor((this.signals.length || 156) * 0.6),
                        sellSignals: Math.floor((this.signals.length || 156) * 0.4),
                        activePairs: 45
                    };
                });
        },

        loadDashboardSignals() {
            ApiService.getDashboardLatestSignals(5)
                .then(response => {
                    this.dashboardSignals = response.data;
                })
                .catch(error => {
                    console.error('获取首页信号失败:', error);
                    // 降级使用信号列表数据
                    this.dashboardSignals = this.signals.slice(0, 5);
                });
        },

        initChart() {
            ApiService.getDashboardChart()
                .then(response => {
                    const chartData = response.data.chartData;
                    this.createChart(chartData);
                })
                .catch(error => {
                    console.error('获取图表数据失败:', error);
                    // 显示空数据
                    const emptyData = {
                        labels: ['9月', '10月', '11月', '12月', '1月', '2月'],
                        buyData: [0, 0, 0, 0, 0, 0],
                        sellData: [0, 0, 0, 0, 0, 0]
                    };
                    this.createChart(emptyData);
                });
        },

        createChart(chartData) {
            const ctx = document.getElementById('signalChart').getContext('2d');
            this.signalChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: chartData.labels,
                    datasets: [{
                        label: '买入信号',
                        data: chartData.buyData,
                        borderColor: 'rgb(16, 185, 129)',
                        backgroundColor: 'rgba(16, 185, 129, 0.1)',
                        tension: 0.4,
                        fill: true
                    }, {
                        label: '卖出信号',
                        data: chartData.sellData,
                        borderColor: 'rgb(239, 68, 68)',
                        backgroundColor: 'rgba(239, 68, 68, 0.1)',
                        tension: 0.4,
                        fill: true
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            labels: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--text-secondary'),
                                usePointStyle: true,
                                padding: 20
                            }
                        },
                        tooltip: {
                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                            titleColor: '#fff',
                            bodyColor: '#fff',
                            cornerRadius: 8
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            ticks: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--text-secondary')
                            },
                            grid: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--border-color')
                            }
                        },
                        x: {
                            ticks: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--text-secondary')
                            },
                            grid: {
                                color: getComputedStyle(document.documentElement).getPropertyValue('--border-color')
                            }
                        }
                    },
                    elements: {
                        point: {
                            radius: 4,
                            hoverRadius: 8
                        }
                    }
                }
            });
        },

        fetchSignals() {
            this.loading = true;
            const payload = {
                search: this.filter.search,
                signalType: this.filter.signalType,
                strategy: this.filter.strategy,
                exchange: this.filter.exchange,
                startDate: this.filter.startDate,
                endDate: this.filter.endDate,
                page: this.currentPage,
                size: ITEMS_PER_PAGE
            };

            ApiService.getSignalsList(payload)
                .then(response => {
                    this.signals = response.data.content;
                    this.totalPages = response.data.totalPages;
                })
                .catch(error => {
                    console.error('获取信号失败:', error);
                    this.showToast('获取信号失败', 'error');
                })
                .finally(() => {
                    this.loading = false;
                });
        },

        refreshSignals() {
            if (this.currentTab === 'dashboard') {
                this.loadDashboardSignals();
                this.loadDashboardStats();
                this.showToast('首页数据已刷新');
            } else {
                this.fetchSignals();
                this.showToast('数据已刷新');
            }
        },

        applyFilter() {
            this.currentPage = 1;
            this.selectedSignal = null;
            this.fetchSignals();
        },

        resetFilter() {
            this.filter = {
                search: '',
                signalType: '',
                strategy: '',
                exchange: '',
                startDate: '',
                endDate: ''
            };
            this.currentPage = 1;
            this.selectedSignal = null;
            this.fetchSignals();
        },

        changePage(page) {
            this.currentPage = page;
            this.selectedSignal = null;
            this.fetchSignals();
        },

        prevPage() {
            if (this.currentPage > 1) {
                this.currentPage--;
                this.fetchSignals();
            }
        },

        nextPage() {
            if (this.currentPage < this.totalPages) {
                this.currentPage++;
                this.fetchSignals();
            }
        },

        showDetails(signal) {
            this.selectedSignal = signal;
            this.showDetailsModal = true;
        },

        closeDetailsModal() {
            this.showDetailsModal = false;
            this.selectedSignal = null;
        },

        formattedSignalTime(time) {
            const date = new Date(time);
            return date.toLocaleString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        },

        formatProfitLossRatio(ratio) {
            if (ratio === null || ratio === undefined) {
                return '未设置';
            }
            return `${parseFloat(ratio).toFixed(2)}:1`;
        },

        // 策略管理方法
        async loadStrategies() {
            console.log('loadStrategies开始执行...');
            this.loading = true;
            try {
                console.log('发送API请求到: /api/strategies');
                const response = await fetch('/api/strategies');
                console.log('API响应状态:', response.status);
                
                const data = await response.json();
                console.log('API响应数据:', data);
                
                // 后端直接返回策略数组，不是包装的格式
                if (Array.isArray(data)) {
                    this.strategies = data;
                    console.log('成功加载策略:', this.strategies.length, '个');
                } else {
                    console.error('响应格式不正确:', data);
                    this.showToast('加载策略失败: 响应格式不正确', 'error');
                }
            } catch (error) {
                console.error('加载策略失败:', error);
                this.showToast('加载策略失败: ' + error.message, 'error');
            } finally {
                this.loading = false;
                console.log('loadStrategies执行完毕');
            }
        },

        showUploadModal() {
            this.showUploadStrategyModal = true;
            this.selectedFile = null;
            this.strategyDescription = '';
        },

        closeUploadModal() {
            this.showUploadStrategyModal = false;
            this.selectedFile = null;
            this.strategyDescription = '';
            if (this.$refs.strategyFileInput) {
                this.$refs.strategyFileInput.value = '';
            }
        },

        onFileSelected(event) {
            const file = event.target.files[0];
            if (file) {
                if (file.size > 10 * 1024 * 1024) { // 10MB
                    this.showToast('文件大小不能超过10MB', 'error');
                    event.target.value = '';
                    return;
                }
                if (!file.name.endsWith('.py')) {
                    this.showToast('只支持.py格式的文件', 'error');
                    event.target.value = '';
                    return;
                }
                this.selectedFile = file;
            }
        },

        async uploadStrategy() {
            if (!this.selectedFile) {
                this.showToast('请选择文件', 'error');
                return;
            }

            this.uploading = true;
            const formData = new FormData();
            formData.append('file', this.selectedFile);
            if (this.strategyDescription) {
                formData.append('description', this.strategyDescription);
            }

            try {
                const response = await fetch('/api/strategies/upload', {
                    method: 'POST',
                    body: formData
                });
                
                const result = await response.json();
                
                if (result.success) {
                    this.showToast('策略上传成功', 'success');
                    this.closeUploadModal();
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || '上传失败', 'error');
                }
            } catch (error) {
                console.error('上传策略失败:', error);
                this.showToast('上传策略失败: ' + error.message, 'error');
            } finally {
                this.uploading = false;
            }
        },

        async downloadStrategy(strategy) {
            try {
                const response = await fetch(`/api/strategies/${strategy.id}/download`);
                if (response.ok) {
                    const blob = await response.blob();
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = strategy.filename;
                    document.body.appendChild(a);
                    a.click();
                    window.URL.revokeObjectURL(url);
                    document.body.removeChild(a);
                    this.showToast('下载成功', 'success');
                } else {
                    this.showToast('下载失败', 'error');
                }
            } catch (error) {
                console.error('下载策略失败:', error);
                this.showToast('下载策略失败: ' + error.message, 'error');
            }
        },

        async hotReloadStrategy(strategy) {
            try {
                const response = await fetch(`/api/strategies/${strategy.id}/reload`, {
                    method: 'POST'
                });
                const result = await response.json();
                
                if (result.success) {
                    this.showToast('热更新成功', 'success');
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || '热更新失败', 'error');
                }
            } catch (error) {
                console.error('热更新失败:', error);
                this.showToast('热更新失败: ' + error.message, 'error');
            }
        },

        deleteStrategy(strategy) {
            this.strategyToDelete = strategy;
            this.showDeleteConfirmModal = true;
        },

        closeDeleteConfirmModal() {
            this.showDeleteConfirmModal = false;
            this.strategyToDelete = null;
        },

        async confirmDeleteStrategy() {
            if (!this.strategyToDelete) return;
            
            this.deleting = true;
            try {
                const response = await fetch(`/api/strategies/${this.strategyToDelete.id}`, {
                    method: 'DELETE'
                });
                const result = await response.json();
                
                if (result.success) {
                    this.showToast('策略删除成功', 'success');
                    this.closeDeleteConfirmModal();
                    this.loadStrategies();
                } else {
                    this.showToast(result.message || '删除失败', 'error');
                }
            } catch (error) {
                console.error('删除策略失败:', error);
                this.showToast('删除策略失败: ' + error.message, 'error');
            } finally {
                this.deleting = false;
            }
        },

        formattedTime(time) {
            const date = new Date(time);
            return date.toLocaleString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        },

        formatFileSize(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        },

        getStatusIcon(status) {
            const icons = {
                'ACTIVE': 'fas fa-check-circle',
                'INACTIVE': 'fas fa-pause-circle',
                'UPDATING': 'fas fa-sync-alt fa-spin',
                'ERROR': 'fas fa-exclamation-circle'
            };
            return icons[status] || 'fas fa-question-circle';
        },

        getStatusText(status) {
            const texts = {
                'ACTIVE': '已激活',
                'INACTIVE': '未激活',
                'UPDATING': '更新中',
                'ERROR': '错误'
            };
            return texts[status] || '未知';
        },

        // 回测相关方法
        initBacktestDates() {
            const today = new Date();
            const oneYearAgo = new Date(today.getFullYear() - 1, today.getMonth(), today.getDate());
            
            this.backtest.startDate = oneYearAgo.toISOString().split('T')[0];
            this.backtest.endDate = today.toISOString().split('T')[0];
        },



        async runBacktest() {
            if (!this.backtest.strategyName || !this.backtest.symbol || 
                !this.backtest.startDate || !this.backtest.endDate) {
                this.showToast('请填写所有必填字段', 'warning');
                return;
            }

            this.backtest.running = true;
            this.backtest.results = null;
            
            try {
                const request = {
                    strategy_name: this.backtest.strategyName,
                    symbol: this.backtest.symbol,
                    start_date: this.backtest.startDate,
                    end_date: this.backtest.endDate,
                    initial_balance: this.backtest.initialBalance
                };

                const response = await fetch('/api/backtest/run', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(request)
                });

                const result = await response.json();
                
                if (result.success && result.data) {
                    this.backtest.results = result.data;
                    this.showToast('回测完成', 'success');
                } else {
                    this.showToast(result.message || '回测失败', 'error');
                }
            } catch (error) {
                console.error('回测请求失败:', error);
                this.showToast('回测请求失败: ' + error.message, 'error');
            } finally {
                this.backtest.running = false;
            }
        },

        resetBacktestForm() {
            this.backtest.strategyName = '';
            this.backtest.symbol = 'BTCUSDT';
            this.backtest.initialBalance = 10000;
            this.backtest.results = null;
            this.initBacktestDates();
        },

        toggleMobileFilter(event) {
            // 防止事件冒泡和重复触发
            if (event) {
                event.preventDefault();
                event.stopPropagation();
            }
            
            // 添加节流防止快速点击
            if (this.filterToggleTimeout) {
                return;
            }
            
            this.filterToggleTimeout = setTimeout(() => {
                this.filterToggleTimeout = null;
            }, 100);
            
            this.mobileFilterExpanded = !this.mobileFilterExpanded;
        }
    }
}).mount('#app');

console.log('=== Vue应用挂载完成 ===');