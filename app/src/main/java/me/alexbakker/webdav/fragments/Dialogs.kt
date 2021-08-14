package me.alexbakker.webdav.fragments

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import me.alexbakker.webdav.R
import me.alexbakker.webdav.settings.Account

class Dialogs {
    companion object {
        fun showRemoveAccountsDialog(context: Context, accounts: List<Account>, listener: DialogInterface.OnClickListener) {
            AlertDialog.Builder(context)
                .setTitle(context.resources.getQuantityString(R.plurals.dialog_title_delete_accounts, accounts.size))
                .setMessage(context.resources.getQuantityString(R.plurals.dialog_message_delete_accounts, accounts.size, accounts.size))
                .setPositiveButton(R.string.yes, listener)
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }
}