package com.myanmar.ledger2d.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.myanmar.ledger2d.AppContainer

@Composable fun LedgerNav(container:AppContainer,modifier:Modifier=Modifier){
    val nav=rememberNavController()
    val vm:LedgerViewModel=viewModel { LedgerViewModel(container) }
    val goTab:(String)->Unit={route->nav.navigate(route){popUpTo("home"){saveState=true};launchSingleTop=true;restoreState=true}}
    NavHost(nav,"home",modifier){
        composable("welcome"){WelcomeScreen{nav.navigate("home"){popUpTo("welcome"){inclusive=true}}}}
        composable("home"){HomeScreen(vm,onQuickEntry={nav.navigate("quickEntry")},onAgents={nav.navigate("agentDashboard")},onWinning={nav.navigate("winning")},onClosedDays={nav.navigate("closedDays")},onSettings={nav.navigate("settings")},onLedger={goTab("todayLedger")},onSettlement={goTab("settlement")},onAddAgent={nav.navigate("agentForm/0")},onAddCustomer={nav.navigate("addCustomerHome")},onAgentDashboard={nav.navigate("agentDashboard")},onCustomerDashboard={nav.navigate("customerDashboard")},onNavigate={route->when(route){"home"->goTab("home");"agentDashboard"->nav.navigate("agentDashboard");"customerDashboard"->nav.navigate("customerDashboard");"closedDays"->nav.navigate("closedDays");"winning"->nav.navigate("winning");"settings"->nav.navigate("settings")}})}
        composable("todayLedger"){TodayLedgerScreen(vm,{nav.popBackStack()},{route->when(route){"home"->goTab("home");"ledger"->goTab("todayLedger");"settlement"->goTab("settlement");"manage"->goTab("agents");"quick"->nav.navigate("quickEntry")}})}
        composable("settlement"){SettlementScreen(vm,{nav.popBackStack()},{route->when(route){"home"->goTab("home");"ledger"->goTab("todayLedger");"settlement"->goTab("settlement");"manage"->goTab("agents");"quick"->nav.navigate("quickEntry")}})}
        composable("quickEntry"){QuickEntryScreen(vm,onBet={agentId,customerId->nav.navigate("bet/$agentId/$customerId")}){nav.popBackStack()}}
        composable("agents"){AgentListScreen(vm,{nav.navigate("agent/$it")},{nav.navigate("agentForm/0")},{nav.navigate("agentForm/$it")},{nav.navigate("winning")},{nav.navigate("closedDays")},{nav.navigate("settings")})}
        composable("agentDashboard"){AgentDashboardScreen(vm,{nav.popBackStack()},{nav.navigate("agent/$it")},{nav.navigate("agentForm/0")},{nav.navigate("agentScope/$it")})}
        composable("agentScope/{feature}",listOf(navArgument("feature"){type=NavType.StringType})){e->AgentScopeScreen(vm,e.arguments!!.getString("feature") ?: "total",{nav.popBackStack()}){feature,id->when(feature){"total"->if(id==-1L)nav.navigate("allAgentFeature/total") else nav.navigate("total/$id");"report"->if(id==-1L)nav.navigate("allAgentFeature/report") else nav.navigate("report/agent/$id");"closed"->if(id==-1L)nav.navigate("allAgentFeature/closed") else nav.navigate("closed/$id");"winning"->if(id==-1L)nav.navigate("allAgentFeature/winning") else nav.navigate("winning/agent/$id");"limit"->if(id==-1L)nav.navigate("allAgentFeature/limit") else nav.navigate("agentLimit/$id")}}}
        composable("allAgentFeature/{feature}",listOf(navArgument("feature"){type=NavType.StringType})){e->AllAgentFeatureScreen(vm,e.arguments!!.getString("feature") ?: "total",{nav.popBackStack()})}
        composable("customerDashboard"){CustomerDashboardScreen(vm,{nav.popBackStack()},{nav.navigate("customer/$it")},{nav.navigate("customerScope/$it")})}
        composable("customerScope/{feature}",listOf(navArgument("feature"){type=NavType.StringType})){e->CustomerScopeScreen(vm,e.arguments!!.getString("feature") ?: "history",{nav.popBackStack()}){feature,agentId,customerId->if(customerId==-1L)nav.navigate("allCustomerScope/$agentId/$feature") else when(feature){"history"->nav.navigate("betHistory/$customerId");"report"->nav.navigate("report/customer/$customerId");"analysis"->nav.navigate("analysis/$customerId");"digits"->nav.navigate("digitList/$agentId/$customerId");"commission"->nav.navigate("commission/$customerId");"winning"->nav.navigate("winning/customer/$customerId")}}}
        composable("allCustomerScope/{agentId}/{feature}",listOf(navArgument("agentId"){type=NavType.LongType},navArgument("feature"){type=NavType.StringType})){e->AllCustomerScopeScreen(vm,e.arguments!!.getLong("agentId"),e.arguments!!.getString("feature") ?: "history",{nav.popBackStack()})}
        composable("addCustomerHome"){AddCustomerFromHomeScreen(vm,{nav.popBackStack()}){nav.navigate("customerForm/$it/0")}}
        composable("agent/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->AgentDetailScreen(vm,e.arguments!!.getLong("id"),{nav.popBackStack()}){nav.navigate(it)}}
        composable("agentForm/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->AgentFormScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
        composable("customers/{agentId}",listOf(navArgument("agentId"){type=NavType.LongType})){e->val a=e.arguments!!.getLong("agentId");CustomerListScreen(vm,a,{nav.popBackStack()},{nav.navigate("customer/$it")},{nav.navigate("customerForm/$a/0")},{nav.navigate("customerForm/$a/$it")})}
        composable("customerForm/{agentId}/{id}",listOf(navArgument("agentId"){type=NavType.LongType},navArgument("id"){type=NavType.LongType})){e->CustomerFormScreen(vm,e.arguments!!.getLong("id"),e.arguments!!.getLong("agentId")){nav.popBackStack()}}
        composable("customer/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->CustomerDetailScreen(vm,e.arguments!!.getLong("id"),{nav.popBackStack()}){nav.navigate(it)}}
        composable("analysis/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->AnalysisScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
        composable("bet/{agentId}/{customerId}",listOf(navArgument("agentId"){type=NavType.LongType},navArgument("customerId"){type=NavType.LongType})){e->BettingScreen(vm,e.arguments!!.getLong("agentId"),e.arguments!!.getLong("customerId"),onBack={nav.popBackStack()})}
        composable("betHistory/{customerId}",listOf(navArgument("customerId"){type=NavType.LongType})){e->BetHistoryScreen(vm,e.arguments!!.getLong("customerId"),{nav.navigate("betEdit/${it.entry.id}")}){nav.popBackStack()}}
        composable("betEdit/{entryId}",listOf(navArgument("entryId"){type=NavType.LongType})){e->val entryId=e.arguments!!.getLong("entryId");val entry by vm.betEntry(entryId).collectAsStateWithLifecycle(initialValue=null);entry?.let{BettingScreen(vm,it.entry.agentId,it.entry.customerId,onBack={nav.popBackStack()},entryId=entryId)}}
        composable("digitList/{agentId}/{customerId}",listOf(navArgument("agentId"){type=NavType.LongType},navArgument("customerId"){type=NavType.LongType})){e->DigitListScreen(vm,e.arguments!!.getLong("agentId"),e.arguments!!.getLong("customerId")){nav.popBackStack()}}
        composable("total/{agentId}",listOf(navArgument("agentId"){type=NavType.LongType})){e->TotalListScreen(vm,e.arguments!!.getLong("agentId")){nav.popBackStack()}}
        composable("closed/{agentId}",listOf(navArgument("agentId"){type=NavType.LongType})){e->ClosedNumberScreen(vm,e.arguments!!.getLong("agentId")){nav.popBackStack()}}
        composable("format"){FormatScreen{nav.popBackStack()}}
        composable("commission/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->CommissionScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
        composable("limit/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->LimitScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
        composable("agentLimit/{id}",listOf(navArgument("id"){type=NavType.LongType})){e->AgentLimitScreen(vm,e.arguments!!.getLong("id")){nav.popBackStack()}}
        composable("winning"){WinningNumberScreen(vm){nav.popBackStack()}}
        composable("closedDays"){ClosedDayScreen(vm){nav.popBackStack()}}
        composable("settings"){SettingsScreen{nav.popBackStack()}}
        composable("winning/{scope}/{id}"){e->val scope=e.arguments?.getString("scope")?:"agent";val id=e.arguments?.getString("id")?.toLongOrNull()?:0;ScopedWinningScreen(vm,scope,id){nav.popBackStack()}}
        composable("report/{scope}/{id}"){e->val scope=e.arguments?.getString("scope")?:"agent";val id=e.arguments?.getString("id")?.toLongOrNull()?:0;ReportScreen(vm,scope,id){nav.popBackStack()}}
    }
}
