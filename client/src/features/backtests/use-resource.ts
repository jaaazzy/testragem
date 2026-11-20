import { useEffect,useState } from 'react'
import { useAuth } from '@/features/auth/store'
import { backtestApi,backtestError } from './api'

// Scope fetched data to its owner, URL, and retry revision. Cancel requests when their scope changes.
export function useResource<T>(path:string) {
  const owner=useAuth(state=>state.user?.id)
  const [revision,setRevision]=useState(0)
  const [state,setState]=useState<{owner?:string;path:string;revision:number;data?:T;error?:string;loading:boolean}>({path,revision:-1,loading:true})
  useEffect(()=>{const controller=new AbortController();if(owner)backtestApi.get<T>(path,controller.signal).then(data=>{if(!controller.signal.aborted)setState({owner,path,revision,data,loading:false})}).catch(error=>{if(!controller.signal.aborted)setState({owner,path,revision,error:backtestError(error),loading:false})});return()=>controller.abort()},[owner,path,revision])
  // Hide old data before the next effect runs. This prevents data from flashing across accounts.
  const current=state.owner===owner&&state.path===path&&state.revision===revision?state:{loading:true,data:undefined,error:undefined}
  return {...current,reload:()=>setRevision(value=>value+1)}
}
