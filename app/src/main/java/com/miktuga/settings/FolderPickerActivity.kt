package com.miktuga.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

class FolderPickerActivity : AppCompatActivity() {

    private lateinit var textCurrentPath: TextView
    private lateinit var listEntries: ListView
    private lateinit var adapter: FolderAdapter

    private lateinit var currentDir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_picker)

        textCurrentPath = findViewById(R.id.textCurrentPath)
        listEntries = findViewById(R.id.listEntries)

        val initial = intent.getStringExtra(EXTRA_INITIAL_PATH)
        currentDir = resolveStartDir(initial)

        adapter = FolderAdapter(layoutInflater)
        listEntries.adapter = adapter
        listEntries.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position)
            navigateTo(entry.target)
        }

        findViewById<View>(R.id.btnCancel).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        findViewById<View>(R.id.btnSelect).setOnClickListener {
            val out = Intent().putExtra(RESULT_PATH, currentDir.absolutePath)
            setResult(Activity.RESULT_OK, out)
            finish()
        }

        // On API 23+ READ_EXTERNAL_STORAGE is a dangerous permission requested at
        // runtime; without this, listFiles() under /storage returns null and the
        // picker shows an empty list. On API 22 (the real Tugella head unit) it's
        // install-time and already granted via the manifest <uses-permission>.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQ_READ_STORAGE
            )
        }

        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_READ_STORAGE) {
            refresh()
        }
    }

    private fun resolveStartDir(path: String?): File {
        if (path != null) {
            val f = File(path)
            if (f.isDirectory) return f
            val parent = f.parentFile
            if (parent != null && parent.isDirectory) return parent
        }
        val storage = File("/storage")
        if (storage.isDirectory) return storage
        return Environment.getExternalStorageDirectory() ?: File("/")
    }

    private fun navigateTo(target: File) {
        if (!target.isDirectory) return
        currentDir = target
        refresh()
    }

    private fun refresh() {
        textCurrentPath.text = currentDir.absolutePath
        val entries = mutableListOf<Entry>()
        val parent = currentDir.parentFile
        if (parent != null && parent.absolutePath != currentDir.absolutePath) {
            entries.add(Entry(parent, getString(R.string.picker_up), isParent = true))
        }
        val children = currentDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?: emptyList()
        for (child in children) {
            entries.add(Entry(child, child.name, isParent = false))
        }
        adapter.setData(entries)
    }

    private data class Entry(val target: File, val display: String, val isParent: Boolean)

    private class FolderAdapter(
        private val inflater: android.view.LayoutInflater
    ) : BaseAdapter() {
        private var items: List<Entry> = emptyList()

        fun setData(newItems: List<Entry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Entry = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_folder, parent, false)
            val icon = view.findViewById<TextView>(R.id.textFolderIcon)
            val name = view.findViewById<TextView>(R.id.textFolderName)
            val entry = items[position]
            icon.text = if (entry.isParent) "↑" else "▸"
            name.text = entry.display
            return view
        }
    }

    companion object {
        const val EXTRA_INITIAL_PATH = "initial_path"
        const val RESULT_PATH = "result_path"
        private const val REQ_READ_STORAGE = 101
    }
}
