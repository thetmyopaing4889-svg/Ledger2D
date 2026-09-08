package com.myanmar.ledger2d.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.myanmar.ledger2d.AppContainer

@Composable fun LedgerNav(container:AppContainer,modifier:Modifier=Modifier){val nav=rememberNavController();val vm:LedgerViewModel=viewModel { LedgerViewModel(container) };NavHost(nav,"welcome",modifier){
    composable("welcome"){WelcomeScreen{nav.navigate("agents"){popUpTo("welcome"){inclusive=true}}}}
    composable("agents"){AgentListScreen(vm,{nav.navigate("agent/$it")},{nav.navigate("agentForm/0")},{nav.navigate("agentForm/$it")},{nav.navigate("winning")})}
    composable("agent/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->AgentDetailScreen(vm,e.arguments!!.getLong("id"),{nav.popBackStack()}){nav.navigate(it)}}
    composable("agentForm/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->AgentFormScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
    composable("customers/{agentId}",listOf(navArgument("agentId"){type=NavType.LongType})){e->val a=e.arguments!!.getLong("agentId");CustomerListScreen(vm,a,{nav.popBackStack()},{nav.navigate("customer/$it")},{nav.navigate("customerForm/$a/0")},{nav.navigate("customerForm/$a/$it")})}
    composable("customerForm/{agentId}/{id}",listOf(navArgument("agentId"){type=NavType.LongType},navArgument("id"){type=NavType.LongType})){e->CustomerFormScreen(vm,e.arguments!!.getLong("id"),e.arguments!!.getLong("agentId")){nav.popBackStack()}}
    composable("customer/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->CustomerDetailScreen(vm,e.arguments!!.getLong("id"),{nav.popBackStack()}){nav.navigate(it)}}
    composable("analysis/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->AnalysisScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
    composable("bet/{agentId}/{customerId}",listOf(navArgument("agentId"){type=NavType.LongType},navArgument("customerId"){type=NavType.LongType})){e->BettingScreen(vm,e.arguments!!.getLong("agentId"),e.arguments!!.getLong("customerId")){nav.popBackStack()}}
    composable("digitList/{agentId}/{customerId}",listOf(navArgument("agentId"){type=NavType.LongType},navArgument("customerId"){type=NavType.LongType})){e->DigitListScreen(vm,e.arguments!!.getLong("agentId"),e.arguments!!.getLong("customerId")){nav.popBackStack()}}
    composable("total/{agentId}",listOf(navArgument("agentId"){type=NavType.LongType})){e->TotalListScreen(vm,e.arguments!!.getLong("agentId")){nav.popBackStack()}}
    composable("closed/{agentId}",listOf(navArgument("agentId"){type=NavType.LongType})){e->ClosedNumberScreen(vm,e.arguments!!.getLong("agentId")){nav.popBackStack()}}
    composable("format"){FormatScreen{nav.popBackStack()}}
    composable("commission/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->CommissionScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
    composable("limit/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->LimitScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
    composable("winning"){WinningNumberScreen(vm){nav.popBackStack()}}
    composable("winning/{scope}/{id}"){e->val scope=e.arguments?.getString("scope")?:"agent";val id=e.arguments?.getString("id")?.toLongOrNull()?:0;ScopedWinningScreen(vm,scope,id){nav.popBackStack()}}
    composable("report/{scope}/{id}"){e->val scope=e.arguments?.getString("scope")?:"agent";val id=e.arguments?.getString("id")?.toLongOrNull()?:0;ReportScreen(vm,scope,id){nav.popBackStack()}}
}}
