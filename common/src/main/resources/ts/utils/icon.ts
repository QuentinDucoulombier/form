export class IconUtils {
    static displayTypeIcon = (code: number) : string => {
        switch (code) {
            case 1 :
                return "/formulaire/public/img/question_type/long-answer.svg";
            case 2 :
                return "/formulaire/public/img/question_type/short-answer.svg";
            case 3 :
                return "/formulaire/public/img/question_type/free-text.svg";
            case 4 :
                return "/formulaire/public/img/question_type/unic-answer.svg";
            case 5 :
                return "/formulaire/public/img/question_type/multiple-answer.svg";
            case 6 :
                return "/formulaire/public/img/question_type/date.svg";
            case 7 :
                return "/formulaire/public/img/question_type/time.svg";
            case 8 :
                return "/formulaire/public/img/question_type/file.svg";
            case 9:
                return "/formulaire/public/img/question_type/singleanswer_radio.svg";
            case 10:
                return "/formulaire/public/img/question_type/matrix.svg";
            case 11:
                return "/formulaire/public/img/question_type/cursor.svg";
            case 12:
                return "/formulaire/public/img/question_type/ranking.svg";
        }
    };
}