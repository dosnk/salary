const dataMasking = {
  手机号脱敏: (phone) => {
    if (!phone || typeof phone !== 'string') return phone;
    if (phone.length !== 11) return phone;
    return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
  },

  身份证脱敏: (idCard) => {
    if (!idCard || typeof idCard !== 'string') return idCard;
    if (idCard.length < 8) return idCard;
    return idCard.replace(/(\d{4})\d*(\d{4})/, '$1********$2');
  },

  邮箱脱敏: (email) => {
    if (!email || typeof email !== 'string') return email;
    const atIndex = email.indexOf('@');
    if (atIndex <= 2) return email;
    const prefix = email.substring(0, 2);
    const suffix = email.substring(atIndex);
    return `${prefix}***${suffix}`;
  },

  姓名脱敏: (name) => {
    if (!name || typeof name !== 'string') return name;
    if (name.length === 2) {
      return name.charAt(0) + '*';
    } else if (name.length > 2) {
      return name.charAt(0) + '*'.repeat(name.length - 2) + name.charAt(name.length - 1);
    }
    return name;
  },

  银行卡脱敏: (cardNo) => {
    if (!cardNo || typeof cardNo !== 'string') return cardNo;
    if (cardNo.length < 8) return cardNo;
    return cardNo.replace(/(\d{4})\d*(\d{4})/, '$1********$2');
  },

  通用脱敏: (str, visibleStart = 2, visibleEnd = 2) => {
    if (!str || typeof str !== 'string') return str;
    if (str.length <= visibleStart + visibleEnd) return str;
    const prefix = str.substring(0, visibleStart);
    const suffix = str.substring(str.length - visibleEnd);
    const maskLength = str.length - visibleStart - visibleEnd;
    return `${prefix}${'*'.repeat(maskLength)}${suffix}`;
  },

  对象脱敏: (obj, maskFields = []) => {
    if (!obj || typeof obj !== 'object') return obj;
    
    const result = Array.isArray(obj) ? [...obj] : { ...obj };
    
    for (const field of maskFields) {
      if (result[field] !== undefined) {
        if (field === 'phone' || field === 'mobile') {
          result[field] = dataMasking.手机号脱敏(result[field]);
        } else if (field === 'idCard' || field === 'id_card') {
          result[field] = dataMasking.身份证脱敏(result[field]);
        } else if (field === 'email') {
          result[field] = dataMasking.邮箱脱敏(result[field]);
        } else if (field === 'name' || field === 'realName' || field === 'real_name') {
          result[field] = dataMasking.姓名脱敏(result[field]);
        } else if (field === 'bankCard' || field === 'bank_card') {
          result[field] = dataMasking.银行卡脱敏(result[field]);
        } else {
          result[field] = dataMasking.通用脱敏(result[field]);
        }
      }
    }
    
    return result;
  },

  数组脱敏: (arr, maskFields = []) => {
    if (!Array.isArray(arr)) return arr;
    return arr.map(item => dataMasking.对象脱敏(item, maskFields));
  }
};

module.exports = dataMasking;
