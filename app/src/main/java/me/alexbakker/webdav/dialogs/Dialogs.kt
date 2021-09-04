package me.alexbakker.webdav.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import me.alexbakker.webdav.R
import me.alexbakker.webdav.data.Account

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

        fun showErrorDialog(context: Context, @StringRes message: Int, e: Exception) {
            showErrorDialog(context, message, e, null)
        }

        fun showErrorDialog(context: Context, @StringRes message: Int, error: CharSequence?) {
            showErrorDialog(context, message, error, null)
        }

        private fun showErrorDialog(
            context: Context,
            @StringRes message: Int,
            e: Exception,
            listener: DialogInterface.OnClickListener?
        ) {
            showErrorDialog(context, message, e.toString(), listener)
        }

        private fun showErrorDialog(
            context: Context,
            @StringRes message: Int,
            error: CharSequence?,
            listener: DialogInterface.OnClickListener?
        ) {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_error, null)
            val textDetails = view.findViewById<TextView>(R.id.error_details)
            textDetails.text = error
            val textMessage = view.findViewById<TextView>(R.id.error_message)
            textMessage.setText(message)
            val dialog = AlertDialog.Builder(context)
                .setTitle(R.string.dialog_title_error)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { dialog1, which ->
                    listener?.onClick(dialog1, which)
                }
                .setNeutralButton(R.string.action_details) { _, _ ->
                    textDetails.visibility = View.VISIBLE
                }
                .create()
            dialog.setOnShowListener {
                val button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                button.setOnClickListener {
                    if (textDetails.visibility == View.GONE) {
                        textDetails.visibility = View.VISIBLE
                        button.setText(R.string.action_copy)
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("text/plain", error)
                        clipboard.setPrimaryClip(clip)
                    }
                }
            }
            dialog.show()
        }
    }
}