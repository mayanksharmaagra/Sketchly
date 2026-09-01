package com.jrprofessor.sketchly.ui.screens.started

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jrprofessor.sketchly.R
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTypography
import com.jrprofessor.sketchly.ui.theme.TextMuted

@Composable
fun StartedScreen(onGetStarted:()-> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "logo",
                modifier = Modifier.size(150.dp)
            )
            Text(
                text = "Sketchly",
                style = SketchlyTypography.displaySmall,
                color = AppNameColor,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "A tiny handwritten note probably won't change the world — but it might make someone smile for a few seconds.",
                style = SketchlyTypography.bodyLarge,
                textAlign = TextAlign.Center,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
onGetStarted()
                },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonGold,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun StartedScreenPreview() {
    StartedScreen(onGetStarted ={

    })
}