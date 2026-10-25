package ma.workshop.invoice.ui.backup

import ma.workshop.invoice.domain.backup.AlertKind
import ma.workshop.invoice.domain.backup.BackupAlert
import ma.workshop.invoice.domain.backup.BackupDestination
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.BackupFrequency
import ma.workshop.invoice.domain.backup.BackupPlan
import ma.workshop.invoice.ui.Strings

/** What went wrong, in plain Arabic. */
fun BackupFailure.message(): String = when (this) {
    BackupFailure.NO_PLAY_SERVICES -> "خدمات Google غير متوفرة أو تحتاج تحديثاً في هذا الهاتف"
    BackupFailure.DEVELOPER_ERROR -> "إعداد Google Drive لهذا التطبيق غير مكتمل أو لا يطابق توقيعه"
    BackupFailure.DRIVE_API_DISABLED -> "خدمة Google Drive غير مفعلة في مشروع الإعداد"
    BackupFailure.NO_ACCOUNT -> "لا يوجد حساب Google صالح على الهاتف"
    BackupFailure.CONSENT_NEEDED -> "انتهى أو أُلغي إذن الدخول إلى Google Drive"
    BackupFailure.TOKEN_REJECTED -> "رفضت Google الإذن الحالي"
    BackupFailure.ACCESS_DENIED -> "رفضت Google الطلب"
    BackupFailure.NO_NETWORK -> "لا يوجد اتصال بالإنترنت"
    BackupFailure.RATE_LIMITED -> "Google مشغولة الآن"
    BackupFailure.DRIVE_FULL -> "مساحة Google Drive ممتلئة"
    BackupFailure.SERVER_ERROR -> "خطأ مؤقت عند Google"
    BackupFailure.DRIVE_UNKNOWN -> "حدث خطأ غير معروف مع Google Drive"
    BackupFailure.LOCAL_PERMISSION -> "لم تُمنح صلاحية الحفظ في مجلد المستندات"
    BackupFailure.LOCAL_FULL -> "ذاكرة الهاتف ممتلئة"
    BackupFailure.LOCAL_WRITE_FAILED -> "تعذر الحفظ على الهاتف"
    BackupFailure.DATA_INVALID -> "تعذر إنشاء نسخة صحيحة. لم يُحفظ شيء"
}

/** What to do next. */
fun BackupFailure.advice(): String = when (this) {
    BackupFailure.NO_PLAY_SERVICES -> "حدّث خدمات Google Play، أو اكتف بالنسخ على الهاتف"
    BackupFailure.DEVELOPER_ERROR -> "يلزم إكمال إعداد Google Cloud مرة واحدة (انظر docs/google-drive-setup.md). النسخ على الهاتف يعمل"
    BackupFailure.DRIVE_API_DISABLED -> "فعّل Google Drive API في Google Cloud ثم اضغط إعادة الاتصال"
    BackupFailure.NO_ACCOUNT -> "أضف حساب Google في إعدادات الهاتف ثم اضغط إعادة الاتصال"
    BackupFailure.CONSENT_NEEDED -> "اضغط إعادة الاتصال ووافق من جديد"
    BackupFailure.TOKEN_REJECTED -> "اضغط إعادة الاتصال"
    BackupFailure.ACCESS_DENIED -> "اضغط إعادة الاتصال وتأكد أن Google Drive API مفعل"
    BackupFailure.NO_NETWORK -> "ستتم المحاولة تلقائياً عند توفر الإنترنت"
    BackupFailure.RATE_LIMITED -> "ستتم المحاولة لاحقاً تلقائياً"
    BackupFailure.DRIVE_FULL -> "احذف ملفات من Google Drive أو زد المساحة"
    BackupFailure.SERVER_ERROR -> "ستتم المحاولة لاحقاً تلقائياً"
    BackupFailure.DRIVE_UNKNOWN -> "أعد المحاولة. إذا تكرر الخطأ أرسل الرمز الصغير لمن يساعدك"
    BackupFailure.LOCAL_PERMISSION -> "اسمح بالحفظ ثم اضغط انسخ الآن"
    BackupFailure.LOCAL_FULL -> "احذف صوراً أو ملفات من الهاتف ثم أعد المحاولة"
    BackupFailure.LOCAL_WRITE_FAILED -> "أعد المحاولة"
    BackupFailure.DATA_INVALID -> "أعد المحاولة. إذا تكرر الخطأ اطلب المساعدة"
}

fun BackupDestination.label(): String = if (this == BackupDestination.LOCAL) Strings.STATUS_PHONE else Strings.STATUS_DRIVE

fun BackupAlert.text(): String = when (kind) {
    AlertKind.NEEDS_ACTION -> failure?.message().orEmpty()
    AlertKind.OVERDUE -> if (destination == BackupDestination.LOCAL) Strings.ALERT_STALE_PHONE else Strings.ALERT_STALE_DRIVE
}

/** One line under "Backup" in Settings. */
fun backupHint(plan: BackupPlan, alerts: List<BackupAlert>): String = when {
    alerts.isNotEmpty() -> Strings.BACKUP_HINT_ATTENTION
    plan.frequency == BackupFrequency.DAILY -> Strings.BACKUP_HINT_DAILY
    plan.frequency == BackupFrequency.WEEKLY -> Strings.BACKUP_HINT_WEEKLY
    else -> Strings.BACKUP_HINT
}
