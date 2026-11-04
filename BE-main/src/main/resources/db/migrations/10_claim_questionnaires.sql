-- Run after 07, 08 and 09. Category bindings use names once, never hard-coded IDs.
-- Existing claims remain valid; only new submissions require questionnaire answers.
BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TABLE IF NOT EXISTS claim_question_template (
    template_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    category_id BIGINT REFERENCES category(category_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_template_active_category
    ON claim_question_template(category_id) WHERE is_active AND category_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_template_active_code
    ON claim_question_template(code) WHERE is_active;

CREATE TABLE IF NOT EXISTS claim_question (
    question_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES claim_question_template(template_id) ON DELETE RESTRICT,
    question_code VARCHAR(16) NOT NULL,
    label TEXT NOT NULL CHECK (length(btrim(label)) > 0),
    input_type VARCHAR(24) NOT NULL CHECK (input_type IN ('TEXT','CHOICE','CONTENTS','LOST_DATE','DESCRIPTION','EVIDENCE')),
    is_required BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    options JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(options) = 'array'),
    visibility JSONB CHECK (visibility IS NULL OR jsonb_typeof(visibility) = 'object'),
    allow_details BOOLEAN NOT NULL DEFAULT false,
    choice_prompt TEXT,
    UNIQUE (template_id, question_code)
);

CREATE TABLE IF NOT EXISTS claim_answer (
    answer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim(claim_id) ON DELETE RESTRICT,
    question_id BIGINT NOT NULL REFERENCES claim_question(question_id) ON DELETE RESTRICT,
    question_code VARCHAR(16) NOT NULL,
    question_label TEXT NOT NULL,
    answer_text TEXT NOT NULL CHECK (length(btrim(answer_text)) BETWEEN 1 AND 6000),
    section_title VARCHAR(200) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version INTEGER NOT NULL,
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (claim_id, question_code)
);
CREATE INDEX IF NOT EXISTS idx_claim_answer_order ON claim_answer(claim_id, sort_order, answer_id);

-- Version 1 is inserted once; rerunning never rewrites existing question versions or answers.
WITH catalog AS (
    SELECT value AS definition
    FROM jsonb_array_elements($claim_catalog$
[
  {
    "code": "COMMON",
    "title": "Thông tin chung",
    "categoryName": null,
    "questions": [
      {
        "code": "C1",
        "label": "Bạn mất món đồ này vào ngày nào? Có nhớ khoảng thời gian không?",
        "inputType": "LOST_DATE",
        "required": true,
        "sortOrder": 10,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "C2",
        "label": "Bạn có thể đã làm mất hoặc để quên đồ ở đâu?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "C3",
        "label": "Hãy mô tả ít nhất một dấu hiệu riêng hoặc căn cứ cho thấy đồ này là của bạn.",
        "inputType": "DESCRIPTION",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "C4",
        "label": "Bạn có ảnh cũ, hóa đơn hoặc tài liệu nào để đối chiếu không?",
        "inputType": "EVIDENCE",
        "required": false,
        "sortOrder": 40,
        "options": [],
        "visibility": null,
        "allowDetails": false
      }
    ]
  },
  {
    "categoryName": "Điện thoại",
    "questions": [
      {
        "code": "Q1",
        "label": "Điện thoại của bạn thuộc hãng và dòng máy nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 10,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Thân máy màu gì? Có dấu trầy, nứt hoặc đặc điểm nào dễ đối chiếu?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Có dùng ốp hoặc phụ kiện gắn trên máy không? Hãy mô tả màu/kiểu/hình trên ốp.",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 30,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Hình nền hoặc một dấu hiệu ở màn hình khóa có gì đặc trưng?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 40,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Máy đang dùng nhà mạng nào?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 50,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Bạn có thể cung cấp một phần serial hoặc bằng chứng mua/sở hữu máy không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 60,
        "options": [],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "PHONE",
    "title": "Điện thoại"
  },
  {
    "categoryName": "Máy tính xách tay",
    "questions": [
      {
        "code": "Q1",
        "label": "Laptop thuộc hãng, dòng máy nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 10,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Vỏ máy màu gì, có nhãn dán hoặc dấu trầy/nứt nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Máy có để trong túi/balo khi bị mất không?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 30,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": false
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Túi đựng có đặc điểm gì và có những đồ gì bên trong?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q3",
              "values": [
                "YES"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Có bao bảo vệ hoặc phụ kiện đi kèm như sạc, chuột, USB không? Hãy mô tả.",
        "inputType": "CHOICE",
        "required": false,
        "sortOrder": 50,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Có phần serial, ảnh cũ hoặc chứng từ sở hữu nào để đối chiếu không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 60,
        "options": [],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "LAPTOP",
    "title": "Máy tính xách tay"
  },
  {
    "categoryName": "Ví tiền & Giấy tờ cá nhân",
    "questions": [
      {
        "code": "Q1",
        "label": "Bạn đang nhận lại ví hay giấy tờ/thẻ rời?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "WALLET",
            "label": "Ví",
            "detailsRequired": false
          },
          {
            "value": "DOCUMENT",
            "label": "Giấy tờ / Thẻ rời",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Ví màu gì, chất liệu và thương hiệu/kiểu dáng thế nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "WALLET"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Trong ví có những loại đồ, giấy tờ hoặc thẻ nào?",
        "inputType": "CONTENTS",
        "required": true,
        "sortOrder": 30,
        "options": [
          {
            "value": "YES",
            "label": "Có giấy tờ / thẻ",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không có giấy tờ / thẻ",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": true
          }
        ],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "WALLET"
              ]
            }
          ]
        },
        "allowDetails": false,
        "choicePrompt": "Trong ví có giấy tờ hoặc thẻ không?"
      },
      {
        "code": "Q4",
        "label": "Tên ghi trên giấy tờ/thẻ trong ví là gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q3",
              "values": [
                "YES"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Đó là loại giấy tờ/thẻ gì, tên người đứng trên thẻ và đơn vị cấp là gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 50,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "DOCUMENT"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Mặt trước giấy tờ/thẻ có màu hoặc hình nhận dạng nào?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 60,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "DOCUMENT"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q7",
        "label": "Có thể cung cấp vài ký tự cuối của mã thẻ để đối chiếu không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 70,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "DOCUMENT"
              ]
            },
            {
              "questionCode": "Q3",
              "values": [
                "YES"
              ]
            }
          ]
        },
        "allowDetails": false
      }
    ],
    "code": "WALLET",
    "title": "Ví tiền & Giấy tờ cá nhân"
  },
  {
    "categoryName": "Chìa khóa",
    "questions": [
      {
        "code": "Q1",
        "label": "Đây là chìa xe, chìa nhà/văn phòng, chìa cơ sở trường hay loại khác?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "CAR",
            "label": "Chìa xe",
            "detailsRequired": false
          },
          {
            "value": "HOME",
            "label": "Chìa nhà / văn phòng",
            "detailsRequired": false
          },
          {
            "value": "SCHOOL",
            "label": "Chìa cơ sở trường",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Loại khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Chùm có khoảng bao nhiêu chìa? Có remote hoặc thẻ đi kèm không?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Có móc, vòng, dây đeo hoặc thẻ tên đi kèm không?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 30,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": false
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Móc/vòng/dây đeo có màu, hình hoặc chữ gì đặc trưng?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q3",
              "values": [
                "YES"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Chìa dùng cho xe hãng nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 50,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "CAR"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Trên chìa hoặc khóa có tên hãng/chữ khắc nào không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 60,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "HOME",
                "OTHER"
              ]
            }
          ]
        },
        "allowDetails": false
      }
    ],
    "code": "KEYS",
    "title": "Chìa khóa"
  },
  {
    "categoryName": "Balo, Túi xách",
    "questions": [
      {
        "code": "Q1",
        "label": "Đó là balo, túi xách, túi đeo chéo, túi laptop hay loại túi khác?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "BACKPACK",
            "label": "Balo",
            "detailsRequired": false
          },
          {
            "value": "HANDBAG",
            "label": "Túi xách",
            "detailsRequired": false
          },
          {
            "value": "CROSSBODY",
            "label": "Túi đeo chéo",
            "detailsRequired": false
          },
          {
            "value": "LAPTOP_BAG",
            "label": "Túi laptop",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Loại túi khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Túi có thương hiệu/logo nào?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Màu chính, họa tiết hoặc chất liệu túi như thế nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Khi bị mất, bên trong túi có những đồ gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Có ngăn riêng, móc treo, khóa kéo, dấu rách hoặc chữ ghi nào đặc trưng?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 50,
        "options": [],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "BAG",
    "title": "Balo, Túi xách"
  },
  {
    "categoryName": "Trang phục (Clothing & Apparel)",
    "questions": [
      {
        "code": "Q1",
        "label": "Bạn mất áo, quần, áo khoác, mũ, giày/dép hay loại trang phục nào?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "CLOTHING",
            "label": "Áo / quần / áo khoác",
            "detailsRequired": false
          },
          {
            "value": "HAT",
            "label": "Mũ",
            "detailsRequired": false
          },
          {
            "value": "FOOTWEAR",
            "label": "Giày / dép",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Trang phục khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Thương hiệu hoặc logo trên đồ là gì?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Đồ có màu và họa tiết như thế nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Cỡ quần áo hoặc cỡ giày/dép là bao nhiêu?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Chất liệu có đặc điểm gì?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 50,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Có tên viết trên nhãn, dấu sửa, vết rách hoặc vật trong túi áo/quần không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 60,
        "options": [],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "CLOTHING",
    "title": "Trang phục (Clothing & Apparel)"
  },
  {
    "categoryName": "Phụ kiện và Trang sức",
    "questions": [
      {
        "code": "Q1",
        "label": "Món đồ là kính, đồng hồ, nhẫn, vòng, dây chuyền hay phụ kiện khác?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "GLASSES",
            "label": "Kính",
            "detailsRequired": false
          },
          {
            "value": "WATCH",
            "label": "Đồng hồ",
            "detailsRequired": false
          },
          {
            "value": "JEWELRY",
            "label": "Nhẫn / vòng / dây chuyền",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Phụ kiện khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Hãng/logo hoặc kiểu dáng món đồ là gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Đồ có màu và chất liệu gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Đó là kính có độ, kính mát hay kính thời trang?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 40,
        "options": [
          {
            "value": "PRESCRIPTION",
            "label": "Kính có độ",
            "detailsRequired": false
          },
          {
            "value": "SUNGLASSES",
            "label": "Kính mát",
            "detailsRequired": false
          },
          {
            "value": "FASHION",
            "label": "Kính thời trang",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "GLASSES"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Có hộp hoặc phụ kiện đựng đi kèm không, đặc điểm thế nào?",
        "inputType": "CHOICE",
        "required": false,
        "sortOrder": 50,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "GLASSES",
                "WATCH"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Có đá/hạt trang trí nào, màu và cách bố trí thế nào?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 60,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "JEWELRY"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q7",
        "label": "Có chữ khắc, ký hiệu, dấu sửa hoặc dấu hiệu riêng ở mặt trong/mặt sau không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 70,
        "options": [],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "ACCESSORY",
    "title": "Phụ kiện và Trang sức"
  },
  {
    "categoryName": "Văn phòng phẩm",
    "questions": [
      {
        "code": "Q1",
        "label": "Món đồ là sách, vở, bút, hộp bút, tài liệu hay loại khác?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "BOOK",
            "label": "Sách",
            "detailsRequired": false
          },
          {
            "value": "NOTEBOOK",
            "label": "Vở",
            "detailsRequired": false
          },
          {
            "value": "PEN",
            "label": "Bút",
            "detailsRequired": false
          },
          {
            "value": "CASE",
            "label": "Hộp bút / túi tài liệu",
            "detailsRequired": false
          },
          {
            "value": "DOCUMENT",
            "label": "Tài liệu",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Loại khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Tên sách/môn học hoặc thương hiệu món đồ là gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Màu bìa/vỏ và họa tiết có đặc điểm gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Có tên, lớp hoặc ký hiệu nào bạn đã viết lên đồ không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 40,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "BOOK",
                "NOTEBOOK",
                "DOCUMENT"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Có ghi chú, trang đánh dấu hoặc dấu sử dụng nào riêng biệt không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 50,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "BOOK",
                "NOTEBOOK",
                "DOCUMENT"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Trong hộp/túi đựng có những gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 60,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "CASE"
              ]
            }
          ]
        },
        "allowDetails": false
      }
    ],
    "code": "STATIONERY",
    "title": "Văn phòng phẩm"
  },
  {
    "categoryName": "Đồ dùng cá nhân",
    "questions": [
      {
        "code": "Q1",
        "label": "Bạn mất bình nước, hộp cơm, ô, thú bông, tai nghe hay loại đồ cá nhân nào?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "BOTTLE",
            "label": "Bình nước",
            "detailsRequired": false
          },
          {
            "value": "FOOD_CONTAINER",
            "label": "Hộp cơm",
            "detailsRequired": false
          },
          {
            "value": "UMBRELLA",
            "label": "Ô",
            "detailsRequired": false
          },
          {
            "value": "TOY",
            "label": "Thú bông / đồ chơi",
            "detailsRequired": false
          },
          {
            "value": "EARPHONES",
            "label": "Tai nghe",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Đồ cá nhân khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Hãng/logo và màu chính của món đồ là gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Chất liệu, kích thước hoặc dung tích ước lượng thế nào?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Nắp, quai, ống hút, hình dán hoặc chữ ghi có đặc điểm gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "BOTTLE",
                "FOOD_CONTAINER"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Tai nghe có hộp, bao bảo vệ hoặc phụ kiện nào đi kèm?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 50,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "EARPHONES"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Có dấu sử dụng, đường may, hình trang trí hoặc chỗ sửa nào riêng biệt không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 60,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q1",
              "values": [
                "TOY",
                "UMBRELLA",
                "OTHER"
              ]
            }
          ]
        },
        "allowDetails": false
      }
    ],
    "code": "PERSONAL",
    "title": "Đồ dùng cá nhân"
  },
  {
    "categoryName": "Đồ dùng khác",
    "questions": [
      {
        "code": "Q1",
        "label": "Bạn đang nhận lại món đồ gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 10,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Đồ có màu, hình dạng và kích thước gần đúng thế nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Có hãng, logo, ký hiệu hoặc nhãn nào không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Có vỏ/túi hoặc đồ đi kèm không? Nếu có, hãy mô tả.",
        "inputType": "CHOICE",
        "required": false,
        "sortOrder": 40,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "OTHER",
    "title": "Đồ dùng khác"
  },
  {
    "categoryName": "Đồ dùng trẻ em",
    "questions": [
      {
        "code": "Q1",
        "label": "Món đồ là đồ chơi, thú bông, quần áo trẻ em hay loại đồ trẻ em nào?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "TOY",
            "label": "Đồ chơi",
            "detailsRequired": false
          },
          {
            "value": "STUFFED_TOY",
            "label": "Thú bông",
            "detailsRequired": false
          },
          {
            "value": "CLOTHING",
            "label": "Quần áo trẻ em",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Đồ trẻ em khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Món đồ có thương hiệu hoặc nhãn hiệu nào?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Màu chính, hình trang trí và kiểu dáng của món đồ như thế nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Có chữ ghi, nhãn tên, đường may, vết sửa hoặc dấu hiệu riêng nào không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 40,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Có phụ kiện hoặc bộ phận đi kèm không? Hãy mô tả.",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 50,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "CHILDREN",
    "title": "Đồ dùng trẻ em"
  },
  {
    "categoryName": "Máy tính & Thiết bị điện tử",
    "questions": [
      {
        "code": "Q1",
        "label": "Món đồ là máy tính bảng, tai nghe, chuột, bàn phím, sạc hay thiết bị điện tử nào?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "TABLET",
            "label": "Máy tính bảng",
            "detailsRequired": false
          },
          {
            "value": "EARPHONES",
            "label": "Tai nghe",
            "detailsRequired": false
          },
          {
            "value": "MOUSE",
            "label": "Chuột",
            "detailsRequired": false
          },
          {
            "value": "KEYBOARD",
            "label": "Bàn phím",
            "detailsRequired": false
          },
          {
            "value": "CHARGER",
            "label": "Sạc",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Thiết bị điện tử khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Hãng, dòng máy và màu của thiết bị là gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Khi mất, thiết bị có ở trong túi hoặc hộp đựng không?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 30,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": false
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Túi/hộp có đặc điểm gì và bên trong có những đồ nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": {
          "anyOf": [
            {
              "questionCode": "Q3",
              "values": [
                "YES"
              ]
            }
          ]
        },
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Thiết bị có vỏ, bao hoặc ốp bảo vệ không, loại và màu gì?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 50,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Có dây, đầu nối, phụ kiện, hình dán hoặc dấu sử dụng riêng nào đi kèm không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 60,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q7",
        "label": "Bạn có một phần serial hoặc chứng từ/ảnh cũ để đối chiếu không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 70,
        "options": [],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "ELECTRONICS",
    "title": "Máy tính & Thiết bị điện tử"
  },
  {
    "categoryName": "Kính mắt",
    "questions": [
      {
        "code": "Q1",
        "label": "Đây là kính có độ, kính mát hay kính thời trang?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "PRESCRIPTION",
            "label": "Kính có độ",
            "detailsRequired": false
          },
          {
            "value": "SUNGLASSES",
            "label": "Kính mát",
            "detailsRequired": false
          },
          {
            "value": "FASHION",
            "label": "Kính thời trang",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Kính có thương hiệu/logo hoặc ký hiệu hãng nào?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Gọng kính và tròng kính có màu, hình dáng thế nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Gọng làm bằng kim loại, nhựa hay chất liệu khác?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 40,
        "options": [
          {
            "value": "METAL",
            "label": "Kim loại",
            "detailsRequired": false
          },
          {
            "value": "PLASTIC",
            "label": "Nhựa",
            "detailsRequired": false
          },
          {
            "value": "MIXED",
            "label": "Kết hợp",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Chất liệu khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Có hộp, túi hoặc khăn lau đi kèm không? Hãy mô tả.",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 50,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Có chữ khắc, vết xước, chỗ sửa hoặc dấu nhận dạng riêng nào?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 60,
        "options": [],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "EYEWEAR",
    "title": "Kính mắt"
  },
  {
    "categoryName": "Giày dép",
    "questions": [
      {
        "code": "Q1",
        "label": "Đồ là giày thể thao, giày tây, sandal, dép hay loại khác?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "SPORT",
            "label": "Giày thể thao",
            "detailsRequired": false
          },
          {
            "value": "FORMAL",
            "label": "Giày tây",
            "detailsRequired": false
          },
          {
            "value": "SANDAL",
            "label": "Sandal",
            "detailsRequired": false
          },
          {
            "value": "SLIPPERS",
            "label": "Dép",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Loại khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Có thương hiệu hoặc logo nào trên giày/dép?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Màu chính, phối màu và họa tiết thế nào?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Cỡ giày/dép là bao nhiêu?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Chất liệu của giày/dép là gì?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 50,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Bạn mất cả đôi hay một chiếc? Có dấu mòn, rách, chữ ghi hoặc dấu riêng nào?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 60,
        "options": [
          {
            "value": "PAIR",
            "label": "Cả đôi",
            "detailsRequired": false
          },
          {
            "value": "LEFT",
            "label": "Một chiếc bên trái",
            "detailsRequired": false
          },
          {
            "value": "RIGHT",
            "label": "Một chiếc bên phải",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": true
      }
    ],
    "code": "FOOTWEAR",
    "title": "Giày dép"
  },
  {
    "categoryName": "Giấy tờ & Thẻ",
    "questions": [
      {
        "code": "Q1",
        "label": "Đây là giấy tờ hoặc thẻ loại gì?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "IDENTITY",
            "label": "Giấy tờ tùy thân",
            "detailsRequired": false
          },
          {
            "value": "STUDENT",
            "label": "Thẻ sinh viên / thẻ nội bộ",
            "detailsRequired": false
          },
          {
            "value": "BANK",
            "label": "Thẻ ngân hàng",
            "detailsRequired": false
          },
          {
            "value": "LICENSE",
            "label": "Giấy phép lái xe",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Giấy tờ / thẻ khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q2",
        "label": "Họ tên ghi trên giấy tờ/thẻ là gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Đơn vị, trường, ngân hàng hoặc cơ quan nào cấp giấy tờ/thẻ?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Mặt trước có màu, hình hoặc thiết kế gì để đối chiếu?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 40,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Bạn có thể cung cấp vài ký tự cuối của mã thẻ/giấy tờ không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 50,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Có bao thẻ, dây đeo hoặc phụ kiện đi kèm không, đặc điểm thế nào?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 60,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "ID_CARD",
    "title": "Giấy tờ & Thẻ"
  },
  {
    "categoryName": "Bình nước",
    "questions": [
      {
        "code": "Q1",
        "label": "Bình thuộc hãng hoặc loại nào: bình giữ nhiệt, bình nhựa, bình thủy tinh hay loại khác?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 10,
        "options": [
          {
            "value": "THERMOS",
            "label": "Bình giữ nhiệt",
            "detailsRequired": false
          },
          {
            "value": "PLASTIC",
            "label": "Bình nhựa",
            "detailsRequired": false
          },
          {
            "value": "GLASS",
            "label": "Bình thủy tinh",
            "detailsRequired": false
          },
          {
            "value": "OTHER",
            "label": "Loại bình khác",
            "detailsRequired": true
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": true
      },
      {
        "code": "Q2",
        "label": "Màu chính, hình in hoặc họa tiết của bình là gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 20,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q3",
        "label": "Chất liệu và dung tích/kích thước ước lượng của bình thế nào?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 30,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q4",
        "label": "Nắp, quai, tay cầm hoặc ống hút có đặc điểm gì?",
        "inputType": "TEXT",
        "required": true,
        "sortOrder": 40,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q5",
        "label": "Có tên viết, hình dán, vết móp, trầy hoặc dấu riêng nào trên bình không?",
        "inputType": "TEXT",
        "required": false,
        "sortOrder": 50,
        "options": [],
        "visibility": null,
        "allowDetails": false
      },
      {
        "code": "Q6",
        "label": "Bình có bao, túi, dây đeo hoặc phụ kiện nào đi kèm không?",
        "inputType": "CHOICE",
        "required": true,
        "sortOrder": 60,
        "options": [
          {
            "value": "YES",
            "label": "Có",
            "detailsRequired": true
          },
          {
            "value": "NO",
            "label": "Không",
            "detailsRequired": false
          },
          {
            "value": "UNKNOWN",
            "label": "Không rõ / Không áp dụng",
            "detailsRequired": false
          }
        ],
        "visibility": null,
        "allowDetails": false
      }
    ],
    "code": "BOTTLE",
    "title": "Bình nước"
  }
]
$claim_catalog$::jsonb)
), inserted_templates AS (
    INSERT INTO claim_question_template(code, title, category_id, version)
    SELECT definition->>'code', definition->>'title', c.category_id, 1
    FROM catalog LEFT JOIN category c ON lower(c.name) = lower(definition->>'categoryName')
    WHERE definition->>'code' = 'COMMON' OR c.category_id IS NOT NULL
    ON CONFLICT DO NOTHING
    RETURNING template_id, code
), available_templates AS (
    SELECT template_id, code FROM inserted_templates
    UNION ALL
    SELECT template_id, code FROM claim_question_template WHERE version = 1
)
INSERT INTO claim_question(template_id, question_code, label, input_type, is_required,
                           sort_order, options, visibility, allow_details, choice_prompt)
SELECT t.template_id, q->>'code', q->>'label', q->>'inputType',
       (q->>'required')::boolean, (q->>'sortOrder')::integer,
       q->'options', NULLIF(q->'visibility', 'null'::jsonb),
       coalesce((q->>'allowDetails')::boolean, false), q->>'choicePrompt'
FROM catalog
JOIN available_templates t ON t.code = definition->>'code'
CROSS JOIN LATERAL jsonb_array_elements(definition->'questions') q
ON CONFLICT (template_id, question_code) DO NOTHING;

COMMIT;
