package yo.app.free

import android.app.Application

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        billingService = RustoreBillingService(this)
    }
    companion object {
        lateinit var billingService: RustoreBillingService
    }
}