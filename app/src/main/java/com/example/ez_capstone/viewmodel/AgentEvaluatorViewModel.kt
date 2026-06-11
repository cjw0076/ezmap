package com.example.ez_capstone.viewmodel

import androidx.lifecycle.ViewModel
import com.example.ez_capstone.eval.AgentEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AgentEvaluatorViewModel @Inject constructor(
    val evaluator: AgentEvaluator
) : ViewModel()
