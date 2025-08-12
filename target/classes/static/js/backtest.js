const { createApp } = Vue;

createApp({
    data() {
        return {
            themeMode: 'auto',
            loading: false,
            selectOptions: {
                strategyNames: []
            },
            backtest: {
                downloadSymbols: 'BTCUSDT,ETHUSDT',
                downloadStartDate: '',
                downloadEndDate: '',
                downloading: false,
                dataInfo: null,
                
                strategyName: '',
                symbol: 'BTCUSDT',
                timeframe: '1m',
                startDate: '',
                endDate: '',
                initialBalance: 10000,
                running: false,
                results: null
            }
        };
    },
    mounted() {
        this.initTheme();
        this.loadSelectOptions();
        this.initBacktestDates();

        // 每分钟检查一次时间并更新主题
        setInterval(() => {
            if (this.themeMode === 'auto') {
                this.updateThemeByTime();
            }
        }, 60000);
    },
    methods: {
        // 主题相关方法
        initTheme() {
            const savedTheme = localStorage.getItem('theme-mode');
            if (savedTheme && ['light', 'dark', 'auto'].includes(savedTheme)) {
                this.themeMode = savedTheme;
            } else {
                this.themeMode = 'auto';
            }
            
            this.applyTheme();
            
            // 监听系统主题变化
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
                if (this.themeMode === 'auto') {
                    this.applyTheme();
                }
            });
        },

        toggleTheme() {
            const modes = ['light', 'dark', 'auto'];
            const currentIndex = modes.indexOf(this.themeMode);
            this.themeMode = modes[(currentIndex + 1) % modes.length];
            this.applyTheme();
            localStorage.setItem('theme-mode', this.themeMode);
        },

        applyTheme() {
            const root = document.documentElement;
            
            if (this.themeMode === 'auto') {
                const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                root.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
            } else {
                root.setAttribute('data-theme', this.themeMode);
            }
        },

        updateThemeByTime() {
            const hour = new Date().getHours();
            const shouldBeDark = hour < 6 || hour >= 18;
            const root = document.documentElement;
            root.setAttribute('data-theme', shouldBeDark ? 'dark' : 'light');
        },

        getThemeIcon() {
            switch(this.themeMode) {
                case 'light': return 'fas fa-sun';
                case 'dark': return 'fas fa-moon';
                case 'auto': return 'fas fa-circle-half-stroke';
                default: return 'fas fa-circle-half-stroke';
            }
        },

        getThemeTooltip() {
            const titles = {
                'light': '浅色主题',
                'dark': '深色主题',
                'auto': '自动主题'
            };
            return titles[this.themeMode] || '主题切换';
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

        // 回测相关方法
        async loadSelectOptions() {
            try {
                const response = await fetch('/api/select-options');
                const data = await response.json();
                
                if (data.strategyNames) {
                    this.selectOptions.strategyNames = data.strategyNames;
                }
            } catch (error) {
                console.error('加载选项失败:', error);
            }
        },

        initBacktestDates() {
            const today = new Date();
            const oneYearAgo = new Date(today.getFullYear() - 1, today.getMonth(), today.getDate());
            const fourYearsAgo = new Date(today.getFullYear() - 4, today.getMonth(), today.getDate());
            
            this.backtest.startDate = oneYearAgo.toISOString().split('T')[0];
            this.backtest.endDate = today.toISOString().split('T')[0];
            this.backtest.downloadStartDate = fourYearsAgo.toISOString().split('T')[0];
            this.backtest.downloadEndDate = today.toISOString().split('T')[0];
        },

        async batchDownloadData() {
            if (!this.backtest.downloadSymbols || !this.backtest.downloadStartDate || !this.backtest.downloadEndDate) {
                this.showToast('请填写所有下载参数', 'warning');
                return;
            }

            const symbols = this.backtest.downloadSymbols.split(',').map(s => s.trim()).filter(s => s);
            
            this.backtest.downloading = true;
            try {
                this.showToast('正在下载数据，请稍候...', 'info');
                
                const response = await fetch('/api/backtest/batch-download', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        symbols: symbols,
                        startDate: this.backtest.downloadStartDate,
                        endDate: this.backtest.downloadEndDate,
                        timeframe: '1m'
                    })
                });

                const result = await response.json();
                
                if (result.success) {
                    this.showToast(`数据下载完成：${result.data?.success_count || 0}/${result.data?.total_symbols || 0}`, 'success');
                    this.getDataInfo(); // 自动刷新数据信息
                } else {
                    this.showToast(result.message || '数据下载失败', 'error');
                }
            } catch (error) {
                console.error('数据下载失败:', error);
                this.showToast('数据下载失败: ' + error.message, 'error');
            } finally {
                this.backtest.downloading = false;
            }
        },

        async getDataInfo() {
            try {
                const response = await fetch('/api/backtest/data-info');
                const result = await response.json();
                
                if (result.success && result.data) {
                    this.backtest.dataInfo = result.data;
                } else {
                    this.backtest.dataInfo = null;
                    this.showToast(result.message || '获取数据信息失败', 'error');
                }
            } catch (error) {
                console.error('获取数据信息失败:', error);
                this.showToast('获取数据信息失败: ' + error.message, 'error');
            }
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
                    initial_balance: this.backtest.initialBalance,
                    timeframe: this.backtest.timeframe
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
            this.backtest.timeframe = '1m';
            this.backtest.initialBalance = 10000;
            this.backtest.results = null;
            this.backtest.dataInfo = null;
            this.initBacktestDates();
        }
    }
}).mount('#app');