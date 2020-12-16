package me.alexbakker.webdav.fragments

import android.os.Bundle
import android.os.Parcelable
import android.view.*
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.fragment_account.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.alexbakker.webdav.R
import me.alexbakker.webdav.databinding.FragmentAccountBinding
import me.alexbakker.webdav.settings.Account
import me.alexbakker.webdav.settings.Settings
import me.alexbakker.webdav.settings.byUUID
import me.alexbakker.webdav.settings.byUUIDOrNull
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {
    @Inject
    lateinit var settings: Settings

    private lateinit var menu: Menu

    private val args: AccountFragmentArgs by navArgs()
    private lateinit var binding: FragmentAccountBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account, container, false)
        if (args.uuid != null) {
            binding.account = settings.accounts.byUUID(args.uuid!!).copy()
        } else {
            binding.account = Account()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_account, menu)
        this.menu = menu
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val account = binding.account!!
        var result: Result

        when (item.itemId) {
            R.id.action_save -> {
                menu.setGroupEnabled(R.id.menu_action_group, false)
                busyIndicator.visibility = View.VISIBLE

                val job = lifecycleScope.launch(Dispatchers.IO) {
                    account.resetClient()
                    val res = account.client.propFind("/")
                    if (res.isSuccessful) {
                        val oldAccount = settings.accounts.byUUIDOrNull(account.uuid)
                        if (oldAccount == null) {
                            settings.accounts.add(account)
                            result = Result(account.uuid, Action.ADD)
                        } else {
                            settings.accounts[settings.accounts.indexOf(oldAccount)] = account
                            result = Result(account.uuid, Action.EDIT)
                        }
                        settings.save(requireContext())

                        lifecycleScope.launch(Dispatchers.Main) {
                            closeWithResult(Result(account.uuid, Action.ADD))
                        }
                    } else {
                        lifecycleScope.launch(Dispatchers.Main) {
                            Snackbar
                                .make(
                                    requireView(),
                                    "Couldn't establish WebDAV connection: ${res.error?.message}",
                                    BaseTransientBottomBar.LENGTH_SHORT
                                )
                                .show()
                        }
                    }
                }
                job.invokeOnCompletion {
                    if (it == null) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            menu.setGroupEnabled(R.id.menu_action_group, true)
                            busyIndicator.visibility = View.GONE
                        }
                    }
                }
                return true
            }
            R.id.action_delete -> {
                result = Result(account.uuid, Action.REMOVE)
            }
            android.R.id.home -> {
                result = Result(account.uuid, Action.CANCEL)
            }
            else -> {
                return false
            }
        }

        closeWithResult(result)
        return true
    }

    private fun closeWithResult(res: Result) {
        view?.clearFocus()

        findNavController().apply {
            previousBackStackEntry?.savedStateHandle?.set("result", res)
            popBackStack()
        }
    }

    enum class Action {
        ADD, CANCEL, EDIT, REMOVE
    }

    @Parcelize
    data class Result(
        var uuid: UUID,
        var action: Action
    ) : Parcelable
}
