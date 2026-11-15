import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api,TOKEN_KEY } from '../api'
import type {User} from '../types'
export const useAuth=defineStore('auth',()=>{
  const user=ref<User|null>(null)
  async function restore(){if(sessionStorage.getItem(TOKEN_KEY))user.value=await api.me()}
  async function login(account:string,password:string){const data=await api.login({account,password});sessionStorage.setItem(TOKEN_KEY,data.accessToken);user.value=data.user}
  function clear(){sessionStorage.removeItem(TOKEN_KEY);user.value=null}
  async function logout(){try{await api.logout();clear()}catch(e){if(!sessionStorage.getItem(TOKEN_KEY))clear();else throw e}}
  return {user,restore,login,clear,logout}
})
