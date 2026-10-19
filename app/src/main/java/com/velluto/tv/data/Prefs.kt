package com.velluto.tv.data

import android.content.Context

/** Credenziali salvate solo sul dispositivo. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun loadAccount(): Account? {
        val url = sp.getString("url", null) ?: return null
        val user = sp.getString("user", null) ?: return null
        val pass = sp.getString("pass", null) ?: return null
        return Account(url, user, pass)
    }

    fun saveAccount(a: Account) {
        sp.edit().putString("url", a.baseUrl).putString("user", a.username).putString("pass", a.password).apply()
    }

    fun clearAccount() {
        sp.edit().remove("url").remove("user").remove("pass").apply()
    }
}
