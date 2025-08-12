const { createApp } = Vue;

createApp({
    data() {
        return {
            themeMode: 'auto',
            loading: false,
            signals: [],
            selectOptions: {
                strategyNames: [],
                exchangeTypes: []
            },
            filter: {
                search: '',
                signalType: '',
                strategy: '',
                exchange: '',
                startDate: '',
                endDate: ''
            },
            pagination: {
                currentPage: 1,
                pageSize: 20,
                totalElements: 0
            },
            mobileFilterExpanded: false,
            showDetailsModal: false,
            selectedSignal: null,
            filterTimeout: null
        };
    },
    computed: {
        hasActiveFilters() {
            return !!(this.filter.search || this.filter.signalType || this.filter.strategy || 
                     this.filter.exchange || this.filter.startDate || this.filter.endDate);
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
        },
        currentPage() {
            return this.pagination.currentPage;
        },
        totalPages() {
            return Math.ceil(this.pagination.totalElements / this.pagination.pageSize);
        },
        visiblePages() {
            const total = this.totalPages;
            const current = this.currentPage;
            const visible = [];
            
            const start = Math.max(1, current - 2);
            const end = Math.min(total, current + 2);
            
            for (let i = start; i <= end; i++) {
                visible.push(i);
            }
            
            return visible;
        }
    },
    mounted() {
        this.initTheme();
        this.loadSelectOptions();
        this.loadSignals();

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

        // 信号相关方法
        async loadSelectOptions() {
            try {
                const response = await fetch('/api/select-options');
                const data = await response.json();
                
                if (data.strategyNames) {
                    this.selectOptions.strategyNames = data.strategyNames;
                }
                if (data.exchangeTypes) {
                    this.selectOptions.exchangeTypes = data.exchangeTypes;
                }
            } catch (error) {
                console.error('加载选项失败:', error);
            }
        },

        async loadSignals() {
            this.loading = true;
            try {
                const params = new URLSearchParams({
                    page: this.pagination.currentPage - 1,
                    size: this.pagination.pageSize,
                    ...this.filter
                });

                const response = await fetch(`/api/signals?${params}`);
                const data = await response.json();
                
                if (data && data.content) {
                    this.signals = data.content;
                    this.pagination.totalElements = data.totalElements || 0;
                } else {
                    this.signals = [];
                    this.pagination.totalElements = 0;
                }
            } catch (error) {
                console.error('加载信号失败:', error);
                this.showToast('加载信号失败: ' + error.message, 'error');
            } finally {
                this.loading = false;
            }
        },

        debounceFilter() {
            if (this.filterTimeout) {
                clearTimeout(this.filterTimeout);
            }
            this.filterTimeout = setTimeout(() => {
                this.applyFilter();
            }, 500);
        },

        applyFilter() {
            this.pagination.currentPage = 1;
            this.loadSignals();
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
            this.applyFilter();
        },

        refreshSignals() {
            this.loadSignals();
        },

        toggleMobileFilter(event) {
            if (window.innerWidth <= 768) {
                this.mobileFilterExpanded = !this.mobileFilterExpanded;
            }
        },

        // 分页相关方法
        changePage(page) {
            if (page !== this.pagination.currentPage && page >= 1 && page <= this.totalPages) {
                this.pagination.currentPage = page;
                this.loadSignals();
            }
        },

        prevPage() {
            if (this.pagination.currentPage > 1) {
                this.pagination.currentPage--;
                this.loadSignals();
            }
        },

        nextPage() {
            if (this.pagination.currentPage < this.totalPages) {
                this.pagination.currentPage++;
                this.loadSignals();
            }
        },

        // 信号详情相关方法
        showDetails(signal) {
            this.selectedSignal = signal;
            this.showDetailsModal = true;
        },

        closeDetailsModal() {
            this.showDetailsModal = false;
            this.selectedSignal = null;
        },

        // 格式化方法
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
            if (!ratio) return '无';
            return `${ratio.toFixed(2)}:1`;
        }
    }
}).mount('#app');