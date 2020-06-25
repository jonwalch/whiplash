export const baseUrl = process.env.NODE_ENV === 'development' ? 'http://localhost:3000/' : "https://" + document.location.host + "/";
export const embedBaseUrl = process.env.NODE_ENV === 'development' ? 'localhost' : document.location.host;
