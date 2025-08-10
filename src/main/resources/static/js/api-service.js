// API服务模块 - 统一管理所有网络请求
class ApiService {

    // 获取设置信息
    static getSettings() {
        return axios.get('/api/getSettings');
    }

    // 保存设置信息
    static saveSettings(settings) {
        return axios.post('/api/saveSettings', settings);
    }

    // 获取选项数据
    static getSelectOptions() {
        return axios.get('/api/select-options');
    }

    // 获取仪表板统计数据
    static getDashboardStats() {
        return axios.get('/api/dashboard/stats');
    }

    // 获取仪表板图表数据
    static getDashboardChart() {
        return axios.get('/api/dashboard/chart');
    }

    // 获取信号列表
    static getSignalsList(payload) {
        return axios.post('/api/signals/list', payload);
    }

    // 获取首页最新信号（不受筛选影响）
    static getDashboardLatestSignals(limit = 5) {
        return axios.get(`/api/dashboard/latest-signals?limit=${limit}`);
    }

    // 通用错误处理
    static handleError(error) {
        console.error('API请求失败:', error);
        if (error.response) {
            // 服务器返回错误状态码
            console.error('错误状态:', error.response.status);
            console.error('错误信息:', error.response.data);
        } else if (error.request) {
            // 请求已发出但没有收到响应
            console.error('网络错误:', error.request);
        } else {
            // 其他错误
            console.error('错误:', error.message);
        }
        throw error;
    }

    // 带错误处理的请求包装器
    static async request(requestFn) {
        try {
            return await requestFn();
        } catch (error) {
            this.handleError(error);
        }
    }
}

// 全局错误拦截器
axios.interceptors.response.use(
    response => response,
    error => {
        ApiService.handleError(error);
        return Promise.reject(error);
    }
);

// 请求拦截器 - 可以在这里添加认证头等通用配置
axios.interceptors.request.use(
    config => {
        // 可以在这里添加通用的请求头
        config.headers['Content-Type'] = 'application/json';
        return config;
    },
    error => {
        return Promise.reject(error);
    }
);