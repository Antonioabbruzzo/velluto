package com.velluto.tv.platform

import android.content.Context
import com.velluto.tv.data.Account

/** Credenziali salvate solo sul dispositivo (stesse chiavi delle versioni precedenti). */
class AndroidAccountStore(context: Context) : AccountStore {
    private val sp = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    override fun load(): Account? {
        val url = sp.getString("url", null) ?: return null
        val user = sp.getString("user", null) ?: return null
        val pass = sp.getString("pass", null) ?: return null
        return Account(url, user, pass)
    }

    override fun save(account: Account) {
        sp.edit()
            .putString("url", account.baseUrl)
            .putString("user", account.username)
            .putString("pass", account.password)
            .apply()
    }

    override fun clear() {
        sp.edit().remove("url").remove("user").remove("pass").apply()
    }
}
