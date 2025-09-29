package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}
	
	/**
	 * 未入力チェック
	 * 
	 * @param lmsUserId
	 * @return count が 0 より大きいかどうかを返す
	 * @author 井手祐次郎 - Task.25
	 */
	public boolean notEnterCount(Integer lmsUserId) {
		// 今日の日付（yyyyMMdd）
		String trainingDate = dateUtil.dateToString(new Date(), "yyyyMMdd");
		
		// 未入力件数を取得
		int count = tStudentAttendanceMapper.notEnterCount(lmsUserId, Constants.DB_FLG_FALSE, trainingDate);
		
		return count > 0;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		// 井手祐次郎 - Task.26
		attendanceForm.setHourMaps(attendanceUtil.setHourMap());
		attendanceForm.setMinuteMaps(attendanceUtil.setMinuteMap());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			
			// 井手祐次郎 - Task.26
			if (attendanceManagementDto.getTrainingStartTime() != null
					&& !attendanceManagementDto.getTrainingStartTime().isEmpty()) {
				String[] start = attendanceManagementDto.getTrainingStartTime().split(":");
				dailyAttendanceForm.setStartHour(start[0]);
				dailyAttendanceForm.setStartMinute(start[1]);
			}
			
			// 井手祐次郎 - Task.26
			if (attendanceManagementDto.getTrainingEndTime() != null
					&& !attendanceManagementDto.getTrainingEndTime().isEmpty()) {
				String[] end = attendanceManagementDto.getTrainingEndTime().split(":");
				dailyAttendanceForm.setEndHour(end[0]);
				dailyAttendanceForm.setEndMinute(end[1]);
			}
			
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			
			// 井手祐次郎 - Task.26
			String startHour = dailyAttendanceForm.getStartHour();
			String startMinute = dailyAttendanceForm.getStartMinute();
			String startTimeStr = startHour + ":" + startMinute;
			if (startHour != null && !startHour.isEmpty() && startMinute != null && !startMinute.isEmpty()) {
				dailyAttendanceForm.setTrainingStartTime(startTimeStr);
			} else {
				dailyAttendanceForm.setTrainingStartTime("");
			}
			
			// 井手祐次郎 - Task.26
			String endHour = dailyAttendanceForm.getEndHour();
			String endMinute = dailyAttendanceForm.getEndMinute();
			String endTimeStr = endHour + ":" + endMinute;
			if (endHour != null && !endHour.isEmpty() && endMinute != null && !endMinute.isEmpty()) {
				dailyAttendanceForm.setTrainingEndTime(endTimeStr);
			} else {
				dailyAttendanceForm.setTrainingEndTime("");
			}
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}
	
	/**
	 * 入力チェック
	 * 
	 * @param attendanceForm
	 * @return エラーメッセージ
	 * @author 井手祐次郎 - Task.27
	 */
	public Map<String, List<String>> validateAttendanceForm(AttendanceForm attendanceForm){
		Map<String, List<String>> result = new HashMap<>();
		List<String> errors = new ArrayList<>();
		List<String> fieldErrors = new ArrayList<>();
		
		int statIndex = 0;
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()){
			
			// 備考の文字数チェック
			if (dailyAttendanceForm.getNote() != null && dailyAttendanceForm.getNote().length() > 100) {
				errors.add(messageUtil.getMessage("maxlength", new String[]{"備考", "100"}));
			}
			
			// 出勤時間(時/分)の片方だけ入力チェック
			boolean startHourFilled = dailyAttendanceForm.getStartHour() != null && !dailyAttendanceForm.getStartHour().isEmpty();
			boolean startMinuteFilled = dailyAttendanceForm.getStartMinute() != null && !dailyAttendanceForm.getStartMinute().isEmpty();
			if (startHourFilled ^ startMinuteFilled) {
				errors.add(messageUtil.getMessage("input.invalid", new String[]{"出勤時間"}));
				if (startHourFilled) {
					fieldErrors.add("startMinute-" + statIndex);
				} else {
					fieldErrors.add("startHour-" + statIndex);
				}
			}
			
			// 退勤時間(時/分)の片方だけ入力チェック
			boolean endHourFilled = dailyAttendanceForm.getEndHour() != null && !dailyAttendanceForm.getEndHour().isEmpty();
			boolean endMinuteFilled = dailyAttendanceForm.getEndMinute() != null && !dailyAttendanceForm.getEndMinute().isEmpty();
			if (endHourFilled ^ endMinuteFilled) {
				errors.add(messageUtil.getMessage("input.invalid", new String[]{"退勤時間"}));
				if (endHourFilled) {
					fieldErrors.add("endMinute-" + statIndex);
				} else {
					fieldErrors.add("endHour-" + statIndex);
				}
			}
			
			// 出勤時間に入力なし & 退勤時間入力ありの場合
			boolean startTimeFilled = dailyAttendanceForm.getStartHour() != null && !dailyAttendanceForm.getStartHour().isEmpty()
											&& dailyAttendanceForm.getStartMinute() != null && !dailyAttendanceForm.getStartMinute().isEmpty();
			boolean endTimeFilled = dailyAttendanceForm.getEndHour() != null && !dailyAttendanceForm.getEndHour().isEmpty()
											&& dailyAttendanceForm.getEndMinute() != null && !dailyAttendanceForm.getEndMinute().isEmpty();
			if (!startTimeFilled && endTimeFilled) {
				errors.add(messageUtil.getMessage("attendance.punchInEmpty"));
				fieldErrors.add("startHour-" + statIndex);
				fieldErrors.add("startMinute-" + statIndex);
			}
			
			// 出勤時間が退勤時間よりも後になっている場合
			if (startTimeFilled && endTimeFilled) {
				TrainingTime start = new TrainingTime(dailyAttendanceForm.getStartHour() + dailyAttendanceForm.getStartMinute());
				TrainingTime end = new TrainingTime(dailyAttendanceForm.getEndHour() + dailyAttendanceForm.getEndMinute());
				String startTime = dailyAttendanceForm.getStartHour() + ":" + dailyAttendanceForm.getStartMinute();
				String endTime = dailyAttendanceForm.getEndHour() + ":" + dailyAttendanceForm.getEndMinute();
				if (start.compareTo(end) > 0) {
					errors.add(messageUtil.getMessage("attendance.trainingTimeRange", new String[]{endTime, startTime}));
				}
			}
			
			// 中抜け時間が勤務時間を超えている場合
			if (startTimeFilled && endTimeFilled && dailyAttendanceForm.getBlankTime() != null) {
				TrainingTime start = new TrainingTime(dailyAttendanceForm.getStartHour() + dailyAttendanceForm.getStartMinute());
				TrainingTime end = new TrainingTime(dailyAttendanceForm.getEndHour() + dailyAttendanceForm.getEndMinute());
				int totalMinutes = (end.getHour() * 60 + end.getMinute()) - (start.getHour() * 60 + start.getMinute());
				
				if (dailyAttendanceForm.getBlankTime() > totalMinutes) {
					errors.add(messageUtil.getMessage("attendance.blankTimeError"));
					fieldErrors.add("blankTime-" + statIndex);
				}
			}
			
			statIndex++;
			
		}
		
		
	    result.put("errors", errors);
	    result.put("fieldErrors", fieldErrors);
	    return result;
	}

}
