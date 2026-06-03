
class ApiService {


    static getSettings() {
        return axios.get('/api/getSettings');
    }


    static saveSettings(settings) {
        return axios.post('/api/saveSettings', settings);
    }


    static getSelectOptions() {
        return axios.get('/api/select-options');
    }


    static getDashboardStats() {
        return axios.get('/api/dashboard/stats');
    }


    static getDashboardChart() {
        return axios.get('/api/dashboard/chart');
    }


    static getSignalsList(payload) {
        return axios.post('/api/signals/list', payload);
    }


    static getDashboardLatestSignals(limit = 5) {
        return axios.get(`/api/dashboard/latest-signals?limit=${limit}`);
    }




    static getStrategies() {
        return axios.get('/api/strategies');
    }


    static uploadStrategy(formData) {
        return axios.post('/api/strategies/upload', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        });
    }


    static downloadStrategy(strategyId) {
        return axios.get(`/api/strategies/${strategyId}/download`, {
            responseType: 'blob'
        });
    }


    static hotReloadStrategy(strategyId) {
        return axios.post(`/api/strategies/${strategyId}/reload`);
    }


    static deleteStrategy(strategyId) {
        return axios.delete(`/api/strategies/${strategyId}`);
    }


    static handleError(error) {
        console.error('API request failed:', error);
        if (error.response) {

            console.error('Error status:', error.response.status);
            console.error('Error message:', error.response.data);
        } else if (error.request) {

            console.error('Network error:', error.request);
        } else {

            console.error('Error:', error.message);
        }
        throw error;
    }


    static async request(requestFn) {
        try {
            return await requestFn();
        } catch (error) {
            this.handleError(error);
        }
    }
}


axios.interceptors.response.use(
    response => response,
    error => {
        ApiService.handleError(error);
        return Promise.reject(error);
    }
);


axios.interceptors.request.use(
    config => {

        config.headers['Content-Type'] = 'application/json';
        return config;
    },
    error => {
        return Promise.reject(error);
    }
);
