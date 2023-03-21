package yo.app.free

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yo.app.free.ui.theme.BillingTestTheme

class SubscriptionActivity : ComponentActivity() {
    var isPurchased = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        p("SubscriptionActivity.onCreate()")

        if (savedInstanceState == null) {
            App.billingService.subscriptionActivityOnNewIntent(intent)
        }

        isPurchased.value = App.billingService.isPurchased
        App.billingService.requestPurchases {
            p("purchases requested")
            isPurchased.value = App.billingService.isPurchased
        }

        setContent {
            BillingTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column(
                        verticalArrangement = spacedBy(8.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("productId \"${RUSTORE_TEST_INAPP_ID}\"")
                        SubscribeButton(isPurchased) {
                            App.billingService.startPurchaseFlow(
                                RUSTORE_TEST_INAPP_ID
                            ) {
                                isPurchased.value = App.billingService.isPurchased
                            }
                        }
                        PurchaseIndicator(isPurchased)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        p("SubscriptionActivity.onNewIntent()")

        App.billingService.subscriptionActivityOnNewIntent(intent)
    }
}

@Composable
fun SubscribeButton(isPurchased: MutableState<Boolean>, callback: () -> Unit) {
    Button(onClick = callback) {
        Text(text = "Subscribe")
    }
}

@Composable
fun PurchaseIndicator(isPurchased: MutableState<Boolean>) {
    Text(text = if (isPurchased.value) "Purchased :)" else "Locked :-|")
}


