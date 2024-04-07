package dev.rocli.android.webdav.fragments

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import com.mikepenz.aboutlibraries.ui.LibsSupportFragment

class LicensesFragment : LibsSupportFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
    }
}
