package jp.co.sss.lms.form;

import jakarta.validation.constraints.Size;
import jp.co.sss.lms.util.TrainingTime;
import lombok.Data;

/**
 * 日次の勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class DailyAttendanceForm {

	/** 受講生勤怠ID */
	private Integer studentAttendanceId;
	/** 途中退校日 */
	private String leaveDate;
	/** 日付 */
	private String trainingDate;
	/** 出勤時間 */
	private String trainingStartTime;
	/** 退勤時間 */
	private String trainingEndTime;
	/** 出勤時間(時) */
	private String startHour;	// 井手祐次郎 - Task.26
	/** 出勤時間(分) */
	private String startMinute;	// 井手祐次郎 - Task.26
	/** 退勤時間(時) */
	private String endHour;	// 井手祐次郎 - Task.26
	/** 退勤時間(分) */
	private String endMinute;	// 井手祐次郎 - Task.26
	/** 中抜け時間 */
	private Integer blankTime;
	/** 中抜け時間（画面表示用） */
	private String blankTimeValue;
	/** ステータス */
	private String status;
	/** 備考 */
	@Size(max = 100, message = "{maxlength}")
	private String note;
	/** セクション名 */
	private String sectionName;
	/** 当日フラグ */
	private Boolean isToday;
	/** エラーフラグ */
	private Boolean isError;
	/** 日付（画面表示用） */
	private String dispTrainingDate;
	/** ステータス（画面表示用） */
	private String statusDispName;
	/** LMSユーザーID */
	private String lmsUserId;
	/** ユーザー名 */
	private String userName;
	/** コース名 */
	private String courseName;
	/** インデックス */
	private String index;
	
	/** 出勤/退勤の片方のみ入力チェック */
	public boolean isStartEndTimeValid() {
		boolean startFilled = startHour != null && !startHour.isEmpty() && startMinute != null && !startMinute.isEmpty();
		boolean endFilled = endHour != null && !endHour.isEmpty() && endMinute != null && !endMinute.isEmpty();
		
		if((startFilled && !endFilled) || (!startFilled && endFilled)) {
			return false;
		}
		return true;
	}
	
	/** 出勤時間 < 退勤時間チェック */
	public boolean isTrainingTimeRangeValid() {
		boolean startFilled = startHour != null && !startHour.isEmpty() && startMinute != null && !startMinute.isEmpty();
		boolean endFilled = endHour != null && !endHour.isEmpty() && endMinute != null && !endMinute.isEmpty();
		
		if(startFilled && endFilled) {
			TrainingTime start = new TrainingTime(startHour + ":" + startMinute);
			TrainingTime end = new TrainingTime(endHour + ":" + endMinute);
			return start.compareTo(end) <=0;
		}
		return true;
	}
	
	/** 中抜け時間が勤怠時間を超えていないかチェック */
	public boolean isBlankTimeValid() {
		boolean startFilled = startHour != null && !startHour.isEmpty() && startMinute != null && !startMinute.isEmpty();
		boolean endFilled = endHour != null && !endHour.isEmpty() && endMinute != null && !endMinute.isEmpty();
		
		if (startFilled && endFilled && blankTime !=null) {
			TrainingTime start = new TrainingTime(startHour + ":" + startMinute);
			TrainingTime end = new TrainingTime(endHour + ":" + endMinute);
			int totalMinutes = end.getHour() * 60 + end.getMinute() - (start.getHour() * 60 + start.getMinute());
			return blankTime <= totalMinutes;
		}
		return true;
	}

}
