package com.fitghost.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System Font Family (임시 사용 - 추후 Noto Sans KR로 교체)
val NotoSansKR = FontFamily.Default

// FitGhost Typography based on HTML designs
val FitGhostTypography =
        Typography(
                // App Title (FitGhost 로고)
                displayLarge =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                letterSpacing = (-0.5).sp
                        ),

                // Page Titles (홈, 피팅, 옷장 등)
                headlineLarge =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                letterSpacing = 0.sp
                        ),

                // Section Headers (이미지, 정보 등)
                headlineMedium =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                letterSpacing = 0.sp
                        ),

                // Content Titles
                titleLarge =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 0.sp
                        ),

                // Body Text
                bodyLarge =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                letterSpacing = 0.sp
                        ),
                bodyMedium =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                letterSpacing = 0.sp
                        ),

                // Small Text (descriptions, captions)
                bodySmall =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                letterSpacing = 0.sp
                        ),

                // Button Text
                labelLarge =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 0.sp
                        ),
                labelMedium =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                letterSpacing = 0.sp
                        ),

                // Navigation Labels
                labelSmall =
                        TextStyle(
                                fontFamily = NotoSansKR,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                letterSpacing = 0.sp
                        )
        )
