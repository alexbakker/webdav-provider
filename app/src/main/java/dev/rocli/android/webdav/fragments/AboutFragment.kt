package dev.rocli.android.webdav.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import com.mikepenz.aboutlibraries.LibsBuilder
import com.mikepenz.aboutlibraries.ui.LibsSupportFragment
import dev.rocli.android.webdav.BuildConfig
import dev.rocli.android.webdav.R
import dev.rocli.android.webdav.databinding.FragmentAboutBinding
import dev.rocli.android.webdav.dialogs.ChangelogDialog
import dev.rocli.android.webdav.dialogs.LicenseDialog

class AboutFragment : LibsSupportFragment() {
    private lateinit var binding: FragmentAboutBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_about, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        binding.appVersion.text = getCurrentAppVersion()
        binding.btnLicense.setOnClickListener {
            LicenseDialog.create().show(parentFragmentManager, null)
        }
        binding.btnThirdPartyLicenses.setOnClickListener {
            val opts = LibsBuilder().withAboutMinimalDesign(true)
            val action = AboutFragmentDirections.actionAboutFragmentToLicensesFragment(opts)
            findNavController().navigate(action)
        }
        binding.btnAppVersion.setOnClickListener {
            copyToClipboard(getCurrentAppVersion(), R.string.toast_version_copied)
        }
        binding.btnGithub.setOnClickListener { openUrl(getString(R.string.about_data_github)) }
        binding.btnRoclidev.setOnClickListener { openUrl(getString(R.string.about_data_author_website)) }
        binding.btnEmail.setOnClickListener { openMail(getString(R.string.about_data_email)) }
        binding.btnRate.setOnClickListener { openUrl(getString(R.string.about_data_playstore)) }
        binding.btnChangelog.setOnClickListener {
            ChangelogDialog.create().show(parentFragmentManager, null)
        }
    }

    private fun getCurrentAppVersion(): String {
        if (BuildConfig.DEBUG) {
            return String.format("%s-%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.GIT_HASH, BuildConfig.GIT_BRANCH)
        }

        return BuildConfig.VERSION_NAME
    }

    private fun openUrl(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW)
        browserIntent.data = Uri.parse(url)
        browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        startActivity(browserIntent)
    }

    private fun copyToClipboard(text: String, @StringRes messageId: Int) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val data = ClipData.newPlainText("text/plain", text)
        clipboard.setPrimaryClip(data)
        Toast.makeText(requireContext(), messageId, Toast.LENGTH_SHORT).show()
    }

    private fun openMail(address: String) {
        val mailIntent = Intent(Intent.ACTION_SENDTO)
        mailIntent.data = Uri.parse("mailto:$address")
        mailIntent.putExtra(Intent.EXTRA_EMAIL, address)
        mailIntent.putExtra(Intent.EXTRA_SUBJECT, R.string.app_name)

        startActivity(Intent.createChooser(mailIntent, getString(R.string.about_send_email)))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
    }
}
