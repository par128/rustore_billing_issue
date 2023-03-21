package yo.app.free

import android.app.Application
import android.content.Intent
import android.util.Log
import ru.rustore.sdk.billingclient.RuStoreBillingClient
import ru.rustore.sdk.billingclient.model.purchase.PaymentFinishCode
import ru.rustore.sdk.billingclient.model.purchase.PaymentResult
import ru.rustore.sdk.billingclient.model.purchase.Purchase
import ru.rustore.sdk.billingclient.model.purchase.PurchaseState
import ru.rustore.sdk.billingclient.model.purchase.response.ConfirmPurchaseResponse
import ru.rustore.sdk.billingclient.model.purchase.response.PurchasesResponse
import ru.rustore.sdk.billingclient.provider.logger.ExternalPaymentLogger
import ru.rustore.sdk.core.tasks.OnCompleteListener
import java.util.*

const val RUSTORE_TEST_INAPP_ID = "test_inapp_22"
private const val YOWINDOW_APP_SCHEME = "https://app.yowindow.com/subscription"
private const val RU_STORE_CONSOLE_APP_ID = "937663"

class RustoreBillingService(app: Application) {

    var isPurchased = false

    init {
        RuStoreBillingClient.init(
            application = app,
            consoleApplicationId = RU_STORE_CONSOLE_APP_ID,
            deeplinkScheme = YOWINDOW_APP_SCHEME,
            externalPaymentLoggerFactory = { tag -> PaymentLogger(tag) },
            debugLogs = true
        )
    }

    fun subscriptionActivityOnNewIntent(intent: Intent) {
        p("RuStoreBillingManager.onNewIntent(intent)")
        RuStoreBillingClient.onNewIntent(intent)
    }

    fun startPurchaseFlow(productId: String, callback: (purchaseId: String?) -> Unit) {
        val pendingOrderId = UUID.randomUUID().toString()
        try {
            p("PurchaseProductTask, purchaseProduct()")
            RuStoreBillingClient.purchases.purchaseProduct(
                productId,
                orderId = pendingOrderId,
                quantity = 1,
                developerPayload = "{}"
            )
            .addOnCompleteListener(object : OnCompleteListener<PaymentResult> {
                override fun onFailure(throwable: Throwable) {
                    p("purchaseProduct.onFailure()")
                    callback(null)
                }

                override fun onSuccess(result: PaymentResult) {
                    p("purchaseProduct.onSuccess() result=$result, pendingOrderId=$pendingOrderId")

                    if (result is PaymentResult.InvalidPaymentState
                        || result is PaymentResult.InvalidInvoice
                        || result is PaymentResult.InvoiceResult) {
                        callback(null)
                        return
                    }

                    if (result is PaymentResult.InvalidPurchase) {
                        p("Invalid purchase, result.orderId=${result.orderId}, error code=${result.errorCode}")
                        if (result.orderId == pendingOrderId) {
                            p("TODO delete")
                        }
                        callback(null)
                        return
                    }

                    if (result is PaymentResult.PurchaseResult) {
                        p("PurchaseResult result.finishCode=${result.finishCode}, result.orderId=${result.orderId}")
                        if (result.orderId == pendingOrderId) {
                            if (result.finishCode == PaymentFinishCode.CLOSED_BY_USER
                            || result.finishCode == PaymentFinishCode.UNHANDLED_FORM_ERROR
                            || result.finishCode == PaymentFinishCode.PAYMENT_TIMEOUT
                            || result.finishCode == PaymentFinishCode.DECLINED_BY_SERVER
                            || result.finishCode == PaymentFinishCode.RESULT_UNKNOWN) {
                                p("Purchase error, TODO delete")
                                callback(null)
                            }
                            else if (result.finishCode == PaymentFinishCode.SUCCESSFUL_PAYMENT) {
                                p("SUCCESSFUL_PAYMENT, before confirmPurchase()")
                                confirmPurchase(result.purchaseId)

                                if (result.productId == RUSTORE_TEST_INAPP_ID) {
                                    isPurchased = true
                                }

                                callback(result.purchaseId)
                            }
                        }
                    }
                }
            })
        }
        catch(e: Exception) {
            p("exception: $e")
            return
        }
    }

    private fun confirmPurchase(purchaseId: String) {
        RuStoreBillingClient.purchases.confirmPurchase(
            purchaseId = purchaseId,
            developerPayload = null
        )
        .addOnCompleteListener(object : OnCompleteListener<ConfirmPurchaseResponse> {
            override fun onFailure(throwable: Throwable) {
                p("confirmPurchase(), error=$throwable")
            }

            override fun onSuccess(result: ConfirmPurchaseResponse) {
                p("confirmPurchase.onSuccess()")
                result.errors?.let {
                    p("confirmPurchase.onSuccess() have errors $it")
                    return
                }
            }
        })
    }

    fun requestPurchases(callback:(purchases: List<Purchase>?) -> Unit) {
        p("requestPurchases()")

        RuStoreBillingClient.purchases.getPurchases()
        .addOnCompleteListener(object : OnCompleteListener<PurchasesResponse> {
            override fun onFailure(throwable: Throwable) {
                p("getPurchases.onFailure(), error=$throwable")
                callback(null)
            }

            override fun onSuccess(result: PurchasesResponse) {
                p("getPurchases.onSuccess()")
                result.errors?.let {
                    p("getPurchases.onSuccess() have errors=$it")
                    return
                }

//Cancel and confirm purchases according to these rules
//https://help.rustore.ru/rustore/for_developers/developer-documentation/SDK-connecting-payments/%20consumption-and-withdrawal
/**
Метод отмен покупки необходимо использовать, если:
Метод получения списка покупок вернул покупку со статусом:
PurchaseState.CREATED;
PurchaseState.INVOICE_CREATED;
Метод потребления продукта необходимо использовать, если:
Метод получения списка покупок вернул покупку со статусом:
PurchaseState.PAID.
 */
                val paidPurchases = mutableListOf<Purchase>()

                result.purchases?.forEach {
                    p("purchases, purchaseState=${it.purchaseState}, productId=${it.productId}")

                    if (it.purchaseState == PurchaseState.CREATED
                    || it.purchaseState == PurchaseState.INVOICE_CREATED
                    ) {
                        p("purchase created or invoice_created, TODO: delete")
                    } else if (it.purchaseState == PurchaseState.PAID) {
                        p("purchase paid")
                        val purchaseId = it.purchaseId ?: run {
                            return@forEach
                        }
                        p("confirming....")
                        confirmPurchase(purchaseId)
                    }

                    if (it.purchaseState == PurchaseState.PAID
                        || it.purchaseState == PurchaseState.CONFIRMED
                    ) {
                        paidPurchases.add(it)
                        if (it.productId == RUSTORE_TEST_INAPP_ID) {
                            isPurchased = true
                        }
                    }
                }

                p("paid purchases...")
                paidPurchases.forEach {
                    p("${it.productId}, ${it.description}")
                }
                callback(paidPurchases)
            }
        })
    }

    class PaymentLogger(private val tag: String) : ExternalPaymentLogger {
        override fun d(e: Throwable?, message: () -> String) {
            Log.d(tag, message.invoke(), e)
        }

        override fun e(e: Throwable?, message: () -> String) {
            Log.e(tag, message.invoke(), e)
        }

        override fun i(e: Throwable?, message: () -> String) {
            Log.i(tag, message.invoke(), e)
        }

        override fun v(e: Throwable?, message: () -> String) {
            Log.v(tag, message.invoke(), e)
        }

        override fun w(e: Throwable?, message: () -> String) {
            Log.w(tag, message.invoke(), e)
        }
    }
}