package dev.rocli.android.webdav.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.rocli.android.webdav.R
import dev.rocli.android.webdav.adapters.AccountAdapter
import dev.rocli.android.webdav.data.Account
import dev.rocli.android.webdav.data.AccountDao
import dev.rocli.android.webdav.databinding.FragmentMainBinding
import dev.rocli.android.webdav.dialogs.Dialogs
import dev.rocli.android.webdav.helpers.MetricsHelper
import dev.rocli.android.webdav.provider.WebDavProvider
import javax.inject.Inject


@AndroidEntryPoint
class MainFragment : Fragment() {
    @Inject
    lateinit var accountDao: AccountDao

    private var actionMode: ActionMode? = null
    private lateinit var binding : FragmentMainBinding
    private lateinit var accountAdapter: AccountAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        accountAdapter = AccountAdapter(accountDao.getAll(), Listener())
        binding.rvAccounts.layoutManager = LinearLayoutManager(context)
        binding.rvAccounts.adapter = accountAdapter
        binding.rvAccounts.addItemDecoration(VerticalSpaceItemDecoration(8f))

        val navController = findNavController()
        navController.addOnDestinationChangedListener { _, _, _ ->
            actionMode?.finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = accountDao.count() == 0L
        binding.viewEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvAccounts.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun removeAccounts(accounts: List<Account>) {
        accountDao.delete(*accounts.toTypedArray())

        for (account in accounts) {
            accountAdapter.remove(account)
        }

        updateEmptyState()
    }

    private fun startEditAccount(account: Account) {
        val action = MainFragmentDirections.actionMainFragmentToAccountFragment(getString(R.string.edit_account), account.id)
        findNavController().navigate(action)
    }

    private inner class Listener : AccountAdapter.Listener {
        override fun onAccountClick(account: Account) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(account.rootUri, DocumentsContract.Document.MIME_TYPE_DIR)
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Snackbar
                    .make(
                        requireView(),
                        R.string.error_documentsui,
                        BaseTransientBottomBar.LENGTH_LONG
                    )
                    .setAction(R.string.action_details) {
                        Dialogs.showErrorDialog(requireContext(), R.string.error_documentsui_dialog, e)
                    }
                    .show()
            }
        }

        override fun onAccountSelectionStart() {
            actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(ActionModeListener())
        }

        override fun onAccountSelectionEnd() {
            actionMode?.finish()
        }

        override fun onAccountSelectionChange(accounts: List<Account>) {
            val multiple = accounts.size > 1
            actionMode?.menu?.findItem(R.id.action_edit)?.isVisible = !multiple
            actionMode?.title = resources.getQuantityString(R.plurals.account_selection, accounts.size, accounts.size)
        }
    }

    private inner class ActionModeListener : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val inflater: MenuInflater = mode!!.menuInflater
            inflater.inflate(R.menu.menu_account_action, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item!!.itemId) {
                R.id.action_delete -> {
                    val accounts = accountAdapter.selectedAccounts.toList()
                    Dialogs.showRemoveAccountsDialog(requireContext(), accounts) { _, _ ->
                        removeAccounts(accounts)

                        WebDavProvider.notifyChangeRoots(requireContext())
                    }
                }
                R.id.action_edit -> {
                    val account = accountAdapter.selectedAccounts.first()
                    startEditAccount(account)
                }
            }

            actionMode?.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            accountAdapter.clearSelection()
        }
    }

    private inner class VerticalSpaceItemDecoration(dp: Float) : RecyclerView.ItemDecoration() {
        private val pixels: Int = MetricsHelper.convertDpToPixels(requireContext(), dp)

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.top = pixels
        }
    }
}
