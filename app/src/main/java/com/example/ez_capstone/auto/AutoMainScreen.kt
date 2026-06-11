package com.example.ez_capstone.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * Android Auto main screen — template-based (custom UI not allowed on Auto).
 * SAFETY: Only 2 quick-access items. Voice interaction is primary; tapping is secondary.
 */
class AutoMainScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val itemList = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("집으로")
                    .addText("저장된 집 경로로 안내")
                    .setOnClickListener { invalidate() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("회사로")
                    .addText("저장된 회사 경로로 안내")
                    .setOnClickListener { invalidate() }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("EZmap")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(itemList)
            .build()
    }
}
