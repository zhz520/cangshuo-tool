package com.cangshuo.toolbox.feature.aitext.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.aitext.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AiState(val user: Long?=null,val status: AiStatus?=null,val task: String="SUMMARIZE",val target: String="en",
    val input: String="",val result: AiResult?=null,val busy: Boolean=false,val consent: Boolean=false,val failure: AiFailure?=null) {
    override fun toString()="AiState[redacted]"
}
class AiTextViewModel(private val useCases: AiTextUseCases): ViewModel() {
    private val mutable=MutableStateFlow(AiState())
    val state=mutable.asStateFlow()
    private var job: Job?=null
    private var generation=0L
    init{viewModelScope.launch{useCases.users.collect{user->this@AiTextViewModel.cancel();mutable.value=AiState(user=user);if(user!=null)refresh()}}}
    fun input(value: String){if(!state.value.busy&&value.length<=8000)mutable.update{it.copy(input=value,result=null,failure=null)}}
    fun task(value: String){if(!state.value.busy)mutable.update{it.copy(task=value,result=null,failure=null)}}
    fun target(value: String){if(!state.value.busy)mutable.update{it.copy(target=value,result=null,failure=null)}}
    fun consent(value: Boolean){if(!state.value.busy)mutable.update{it.copy(consent=value)}}
    fun refresh()=operation{user->val status=useCases.status(user);currentCoroutineContext().ensureActive();mutable.update{it.copy(status=status)}}
    fun execute(){val snapshot=state.value;if(snapshot.busy||!snapshot.consent||snapshot.status?.enabled!=true)return
        mutable.update{it.copy(result=null)}
        operation{user->val result=useCases.execute(user,snapshot.task,snapshot.input,snapshot.target);currentCoroutineContext().ensureActive()
            mutable.update{it.copy(result=result,status=it.status?.copy(usedToday=it.status.usedToday+1))}}
    }
    fun cancel(){generation++;job?.cancel();job=null;mutable.update{it.copy(busy=false)}}
    private fun operation(block: suspend(Long)->Unit){val user=state.value.user?:return;if(state.value.busy)return
        val token=++generation;mutable.update{it.copy(busy=true,failure=null)}
        job=viewModelScope.launch{try{block(user)}catch(cancelled: CancellationException){throw cancelled}
            catch(error: AiException){if(token==generation)mutable.update{it.copy(failure=error.failure)}}
            catch(_: Exception){if(token==generation)mutable.update{it.copy(failure=AiFailure.SERVICE)}}
            finally{if(token==generation)mutable.update{it.copy(busy=false)}}}
    }
    companion object{fun factory(useCases: AiTextUseCases): ViewModelProvider.Factory=viewModelFactory{initializer{AiTextViewModel(useCases)}}}
}
